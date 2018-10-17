#!/usr/bin/ruby

require 'json'

def main(path)
   metadata = nil
   File.open(path, 'r'){|file|
      metadata = JSON.parse(file.read())
   }

   numPeople = metadata['size']
   numVotes = numPeople * 2

   numGroundings = 0

   # Prior
   numGroundings += numVotes

   # Functional constraint
   numGroundings += numPeople

   # Bias
   numGroundings += numVotes

   # Each edge type will ground 2x the lesser of its in/out degree.
   # 2x because it will ground out one for each affiliation.
   metadata['edgeCounts'].each{|edgeType, counts|
      numGroundings += 2 * counts.values().min()
   }

   puts numGroundings
end

def loadArgs(args)
   if (args.size() != 1 || args.map{|arg| arg.gsub('-', '').downcase()}.include?('help'))
      puts "USAGE: ruby #{$0} <data metadata file>"
      puts "   Use a data directories metadata file (usually called options.json)"
      puts "   to compute the a rough number of groundings in generated using that data."
      exit(1)
   end

   path = args.shift()

   return path
end

if ($0 == __FILE__)
   main(*loadArgs(ARGV))
end
