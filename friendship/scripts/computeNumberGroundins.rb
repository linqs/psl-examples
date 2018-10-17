#!/usr/bin/ruby

# Take a guess at the number of groundings different configurations will produce.
# Note that the actual number will be reduced if the number of people is not
# divisible by the number of locations or if there are some 0 similarity scores.

def permutation(n, r)
   return factorial(n) / factorial(n - r)
end

def factorial(n)
   return (1..n).reduce(1, :*)
end

def numberGroundings(people, locations)
   # Upper bound if not evenly disivible.
   blockSize = (people.to_f() / locations).ceil()

   return 3 * (permutation(blockSize, 2) * locations) + (permutation(blockSize, 3) * locations)
end

def main(people, locations)
   puts "People: #{people}, Locations: #{locations}, Num Groundings: #{numberGroundings(people, locations)}"
end

def loadArgs(args)
   if (args.size() != 2 || args.map{|arg| arg.gsub('-', '').downcase()}.include?('help'))
      puts "USAGE: ruby #{$0} <num people> <num locations (blocks)>"
      exit(1)
   end

   people = args.shift().to_i()
   locations = args.shift().to_i()

   return people, locations
end

if ($0 == __FILE__)
   main(*loadArgs(ARGV))
end
