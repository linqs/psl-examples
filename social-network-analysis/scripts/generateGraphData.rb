#!/usr/bin/ruby

require 'fileutils'
require 'json'
require 'set'

DEFAULT_SEED = 4

POSITIVE_BIAS_ID = 0
NEGATIVE_BIAS_ID = 1

MISS_TOLERENCE = 1000

POSITIVE_BIAS_NAME = 'Republican'
NEGATIVE_BIAS_NAME = 'Democratic'

BIAS_FILE = 'bias_obs.txt'
PARTY_FILE = 'party_obs.txt'
VOTES_FILE = 'votes_targets.txt'
OPTIONS_FILE = 'options.json'

EDGE_TYPES = {
   'knowswell' =>       {:alpha => 0.40, :gamma => 2.30},
   'knows' =>           {:alpha => 0.77, :gamma => 2.15},
   'idol' =>            {:alpha => 0.85, :gamma => 2.09},
   'boss' =>            {:alpha => 0.85, :gamma => 2.08},
   'mentor' =>          {:alpha => 0.85, :gamma => 2.07},
   'olderRelative' =>   {:alpha => 0.88, :gamma => 2.06},
}

def writeTSV(path, rows)
   File.open(path, 'w'){|file|
      file.puts(rows.map{|row| row.join("\t")}.join("\n"))
   }
end

# Write some additional metadata that is not required 
def writeMeta(outDir, size, seed, edgeCounts)
   # Write out party labels (as shown in the original data).
   rows = [
      [POSITIVE_BIAS_ID, POSITIVE_BIAS_NAME],
      [NEGATIVE_BIAS_ID, NEGATIVE_BIAS_NAME],
   ]
   writeTSV(File.join(outDir, PARTY_FILE), rows)

   # Write out the parameters to this generation.
   options = {
      'size' => size,
      'seed' => seed,
      'edgeTypes' => EDGE_TYPES,
      'edgeCounts' => edgeCounts,
   }

   File.open(File.join(outDir, OPTIONS_FILE), 'w'){|file|
      file.puts(JSON.pretty_generate(options))
   }
end

def writeNodes(outDir, nodes)
   # First write the bias.
   # Separate out the nodes by political affiliation (positive or negative).
   # Then take the absolute value.
   biasRows = []
   nodes.each{|id, value|
      if (value >= 0)
         biasRows << [id, POSITIVE_BIAS_ID, value]
      else
         biasRows << [id, NEGATIVE_BIAS_ID, value.abs()]
      end
   }

   writeTSV(File.join(outDir, BIAS_FILE), biasRows)

   # Now write the targets.
   votesRows = []
   nodes.each_key{|id|
      votesRows << [id, POSITIVE_BIAS_ID]
      votesRows << [id, NEGATIVE_BIAS_ID]
   }

   writeTSV(File.join(outDir, VOTES_FILE), votesRows)
end

def writeEdges(outDir, edges)
   edges.each{|edgeType, rows|
      # We will just throw out and duplicate edges.
      writeTSV(File.join(outDir, "#{edgeType}_obs.txt"), rows.uniq())
   }
end

def samplePowerDistribution(rand, alpha, gamma)
   # return alpha * (rand.rand() ** (-1.0 * gamma))
   # Why -1?
   return -1.0 * ((1.0 - gamma) / (alpha * (rand.rand() ** (1.0 - gamma))))
end

def samplePoliticalAffiliation(rand)
   return rand.rand() * 2.0 - 1.0
end

def timeMills()
   return (Time.now.to_f * 1000).to_i()
end

def main(size, outDir, seed)
   if (File.exists?(outDir))
      puts "Data (#{outDir}) already exists, skipping generation."
      return
   end

   FileUtils.mkdir_p(outDir)
   rand = Random.new(seed)

   # In other structures, we will be referring to nodes by their index in this array.
   nodeIndexes = []

   # We will later drop any nodes without edges.
   nodesWithEdges = Set.new()

   # {type => [[source, dest], ...], ...}
   edges = Hash.new{|hash, key| hash[key] = []}

   # All the nodes that still have available incoming edges.
   # {type => {nodeId => count, ...}, ...}
   inDegrees = Hash.new{|hash, key| hash[key] = {}}

   outDegrees = Hash.new{|hash, key| hash[key] = {}}

   # Initialize all nodes with some political value and sample their degrees.
   for id in 0...size
      nodeIndexes << samplePoliticalAffiliation(rand)

      EDGE_TYPES.each{|edgeType, edgeParams|
         inDegree = samplePowerDistribution(rand, edgeParams[:alpha], edgeParams[:gamma]).to_i()
         outDegree = samplePowerDistribution(rand, edgeParams[:alpha], edgeParams[:gamma]).to_i()

         if (inDegree > 0)
            inDegrees[edgeType][id] = inDegree
         end

         if (outDegree > 0)
            outDegrees[edgeType][id] = outDegree
         end
      }
   end

   # Get some rough counts for each edge type.
   # {edgeType => {:in => count, :out => count}, ...}
   edgeCounts = Hash.new{|hash, key| hash[key] = {:in => 0, :out => 0}}
   inDegrees.each{|edgeType, nodes|
      nodes.each{|id, count|
         edgeCounts[edgeType][:in] += count
      }
   }
   outDegrees.each{|edgeType, nodes|
      nodes.each{|id, count|
         edgeCounts[edgeType][:out] += count
      }
   }

   # Now connect nodes with avaible in/out edges.
   EDGE_TYPES.each_key{|edgeType|
      edgeIn = inDegrees[edgeType]
      edgeOut = outDegrees[edgeType]

      # Trasform the edge maps for better sampling.
      # [[nodeId, count], ...]
      edgeIn = inDegrees[edgeType].to_a()
      edgeOut = outDegrees[edgeType].to_a()

      # The sum of all counts.
      totalEdgeInCount = edgeIn.map{|nodeId, count| count}.inject(0, :+)
      totalEdgeOutCount = edgeOut.map{|nodeId, count| count}.inject(0, :+)

      # Keep track of how many times we select bad edges.
      # When it gets too high, compact the list.
      missCount = 0

      while (true)
         # Stop if there are no more in/out edges left,
         # or if there is one in both but it is the same node.
         # (For efficiency, we are actually not going to see if they are actually the same).
         if (totalEdgeInCount == 0 || totalEdgeOutCount == 0 || (totalEdgeInCount == 1 && totalEdgeOutCount == 1))
            break
         end

         # Randomly choose two nodes and link them up (unless they are the same node).
         sourceIndex = rand.rand(edgeOut.size())
         destIndex = rand.rand(edgeIn.size())

         sourceId = edgeOut[sourceIndex][0]
         destId = edgeIn[destIndex][0]

         if (sourceId == destId)
            next
         end

         # We do not delete edges every time.
         if (edgeOut[sourceIndex][1] == 0 || edgeIn[destIndex][1] == 0)
            missCount += 1

            # If we have missed too much, compact the lists.
            if (missCount >= MISS_TOLERENCE)
               edgeIn = edgeIn.delete_if{|nodeId, count| count == 0}
               edgeOut = edgeOut.delete_if{|nodeId, count| count == 0}

               missCount = 0
            end

            next
         end

         # Do bookkeeping on the degree counts.
         totalEdgeOutCount -= 1
         edgeOut[sourceIndex][1] -= 1

         totalEdgeInCount -= 1
         edgeIn[destIndex][1] -= 1

         # Link up the nodes.
         edges[edgeType] << [sourceId, destId]

         nodesWithEdges.add(sourceId)
         nodesWithEdges.add(destId)
      end
   }

   # Drop any nodes without edges.
   # Note that we will not try to fill the holes left by removed nodes.
   # {id => value, ...}
   nodes = {}
   nodeIndexes.each_with_index{|value, index|
      if (!nodesWithEdges.include?(index))
         next
      end

      nodes[index] = value
   }

   writeNodes(outDir, nodes)
   writeEdges(outDir, edges)
   writeMeta(outDir, size, seed, edgeCounts)
end

def loadArgs(args)
   if (![2, 3].include?(args.size()) || args.map{|arg| arg.gsub('-', '').downcase()}.include?('help'))
      puts "USAGE: ruby #{$0} <number of people> <out dir> [seed]"
      puts "   number of people - the number of people to generate (upper bound)."
      puts "   out dir - the dir that all the data will be dumped (does not have to exist)."
      puts "   seed - the random seed to use. Defaults to: #{DEFAULT_SEED}."
      exit(1)
   end

   size = args.shift().to_i()
   outDir = args.shift()
   seed = DEFAULT_SEED

   if (args.size() > 0)
      seed = args.shift().to_i()
   end

   return size, outDir, seed
end

if ($0 == __FILE__)
   main(*loadArgs(ARGV))
end
