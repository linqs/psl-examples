#!/usr/bin/ruby

# Self pairings are disallowed.

require 'json'
require 'fileutils'

module GenData
   # A directory inside this directory will be created for this data.
   DATA_BASE_DIR = File.join('.', 'data', 'friendship')

   DEFAULT_SEED = 4

   OPTIONS = [
      {
         :id => 'people',
         :short => 'p',
         :long => 'people',
         :valueDesc => 'number of people',
         :desc => 'The number of people to create.',
         :domain => [1, 1000000],
         :default => 10
      },
      {
         :id => 'locations',
         :short => 'l',
         :long => 'locations',
         :valueDesc => 'number of locations',
         :desc => 'The number of locations to create.',
         :domain => [1, 1000000],
         :default => 3
      },
      {
         :id => 'friendshipHigh',
         :short => 'fh',
         :long => 'friendship-high',
         :valueDesc => 'probability',
         :desc => 'The probability that two people in the same location are friends',
         :domain => [0.0, 1.0],
         :default => 1.0
      },
      {
         :id => 'friendshipLow',
         :short => 'fl',
         :long => 'friendship-low',
         :valueDesc => 'probability',
         :desc => 'The probability that two people in different locations are friends',
         :domain => [0.0, 1.0],
         :default => 0.0
      },
      {
         :id => 'similarityMeanHigh',
         :short => 'smh',
         :long => 'similarity-mean-high',
         :valueDesc => 'value',
         :desc => 'The mean of the gaussian distribution to draw high probabilities from.',
         :domain => [0.0, 1.0],
         :default => 0.8
      },
      {
         :id => 'similarityVarianceHigh',
         :short => 'svh',
         :long => 'similarity-variance-high',
         :valueDesc => 'value',
         :desc => 'The variance of the gaussian distribution to draw high probabilities from.',
         :domain => [0.0, 1.0],
         :default => 0.1
      },
      {
         :id => 'similarityMeanLow',
         :short => 'slh',
         :long => 'similarity-mean-low',
         :valueDesc => 'value',
         :desc => 'The mean of the gaussian distribution to draw low probabilities from.',
         :domain => [0.0, 1.0],
         :default => 0.2
      },
      {
         :id => 'similarityVarianceLow',
         :short => 'svl',
         :long => 'similarity-variance-low',
         :valueDesc => 'value',
         :desc => 'The variance of the gaussian distribution to draw low probabilities from.',
         :domain => [0.0, 1.0],
         :default => 0.1
      },
      {
         :id => 'seed',
         :short => 's',
         :long => 'seed',
         :valueDesc => 'value',
         :desc => 'The random seed to use.',
         :domain => [-999999999, 999999999],
         :default => DEFAULT_SEED
      },
      {
         :id => 'name',
         :short => 'n',
         :long => 'name',
         :valueDesc => 'value',
         :desc => 'The base name for this experiment.',
         :domain => nil,
         :default => 'base'
      },
   ]

   def GenData.writeData(dataDir, locations, similarity, friendship, options)
      FileUtils.mkdir_p(dataDir)

      similarObsPath = File.join(dataDir, 'similar_obs.txt')
      locationObsPath = File.join(dataDir, 'location_obs.txt')
      friendsTargetPath = File.join(dataDir, 'friends_targets.txt')
      friendsTruthPath = File.join(dataDir, 'friends_truth.txt')
      optionsPath, = File.join(dataDir, 'options.json')

      File.open(locationObsPath, 'w'){|file|
         file.puts(locations.each_with_index().map{|loc, index| "#{index}\t#{loc}"}.join("\n"))
      }

      File.open(similarObsPath, 'w'){|file|
         file.puts(similarity.map{|entry| entry.join("\t")}.join("\n"))
      }

      File.open(friendsTruthPath, 'w'){|file|
         file.puts(friendship.map{|entry| entry.join("\t")}.join("\n"))
      }

      File.open(friendsTargetPath, 'w'){|file|
         # Make sure to add on the zero for the initial value.
         file.puts(friendship.map{|entry| (entry[0...2].push(0)).join("\t")}.join("\n"))
      }

      # Write out the options as well.
      File.open(optionsPath, 'w'){|file|
         file.puts(JSON.pretty_generate(options))
      }
   end

   # Return the directory the data is in.
   def GenData.genData(options)
      dataDir = File.join(DATA_BASE_DIR, "#{options['name']}_#{'%04d' % options['people']}_#{'%04d' % options['locations']}")

      if (File.exists?(dataDir))
         puts "Data directory (#{dataDir}) already exists, skipping generation."
         return dataDir
      end

      random = Random.new(options['seed'])
      numPeople = options['people']

      locations = []
      for i in 0...numPeople
         locations << random.rand(options['locations'])
      end

      friendship = []
      for i in 0...numPeople
         for j in 0...numPeople
            if (i == j)
               next
            else
               friendshipChance = options['friendshipHigh']
               if (locations[i] != locations[j])
                  friendshipChance = options['friendshipLow']
               end
               
               if (random.rand(1.0) < friendshipChance)
                  friends = 1
               else
                  friends = 0
               end
            end

            friendship << [i, j, friends]
         end
      end

      similarity = []
      for i in 0...numPeople
         for j in 0...numPeople
            if (i == j)
               next
            else
               mean = options['similarityMeanHigh']
               variance = options['similarityVarianceHigh']
               if (locations[i] != locations[j])
                  mean = options['similarityMeanLow']
                  variance = options['similarityVarianceLow']
               end

               sim = GenData.gaussian(mean, variance, random)
            end

            sim = [1.0, [0, sim].max()].min()

            similarity << [i, j, sim]
         end
      end

      GenData.writeData(dataDir, locations, similarity, friendship, options)

      return dataDir
   end

   # Box-Muller: http://www.taygeta.com/random/gaussian.html
   def GenData.gaussian(mean, variance, rng)
      w = 2

      while (w >= 1.0)
         x1 = 2.0 * rng.rand() - 1
         x2 = 2.0 * rng.rand() - 1
         w = x1 ** 2 + x2 ** 2
      end
      w = Math.sqrt((-2.0 * Math.log(w)) / w)

      return x1 * w * Math.sqrt(variance) + mean
   end

   def GenData.loadArgs(args)
      if ((args.map{|arg| arg.gsub('-', '').downcase()} & ['help', 'h']).any?())
         puts "USAGE: ruby #{$0} [OPTIONS]"
         puts "Options:"

         optionsStr = OPTIONS.map{|option|
            "   -#{option[:short]}, --#{option[:long]} <#{option[:valueDesc]}> - Default: #{option[:default]}. Domain: #{option[:domain]}. #{option[:desc]}"
         }.join("\n")
         puts optionsStr
         exit(1)
      end

      optionValues = OPTIONS.map{|option| [option[:id], option[:default]]}.to_h()

      while (args.size() > 0)
         rawFlag = args.shift()
         flag = rawFlag.strip().sub(/^-+/, '')
         currentOption = nil

         OPTIONS.each{|option|
            if ([option[:short], option[:long]].include?(flag))
               currentOption = option
               break
            end
         }

         if (currentOption == nil)
            puts "Unknown option: #{rawFlag}"
            exit(2)
         end

         if (args.size() == 0)
            puts "Expecting value to argument (#{rawFlag}), but found nothing."
            exit(3)
         end

         value = args.shift()
         if (currentOption[:default].is_a?(Integer))
            value = value.to_i()
         elsif (currentOption[:default].is_a?(Float))
            value = value.to_f()
         end

         if (currentOption.has_key?(:domain) && currentOption[:domain] != nil &&
               (value < currentOption[:domain][0] || value > currentOption[:domain][1]))
            puts "Value for #{rawFlag} (#{value}) not in domain: #{currentOption[:domain]}."
            exit(4)
         end

         optionValues[currentOption[:id]] = value
      end

      return optionValues
   end

   def GenData.main(args)
      return GenData.genData(GenData.loadArgs(args))
   end

   # Generate the data with simplified arguments.
   # We will only take the number of people, and use the following
   # non-default arguments:
   #    location (blocks): 10
   #    friendship high: 0.85
   #    friendship low: 0.15
   #    name: friendship
   def GenData.simpleGen(numPeople)
      args = [
         '-p', "#{numPeople}",
         '-l', '10',
         '-fh', '0.85',
         '-fl', '0.15',
         '-n', 'friendship'
      ]

      return GenData.genData(GenData.loadArgs(args))
   end
end

if ($0 == __FILE__)
   GenData.main(ARGV)
end
