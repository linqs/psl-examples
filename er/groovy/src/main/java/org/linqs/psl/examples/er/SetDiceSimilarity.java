/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2017 The Regents of the University of California
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.linqs.psl.examples.er;

import org.linqs.psl.database.DatabaseQuery;
import org.linqs.psl.database.ReadableDatabase;
import org.linqs.psl.database.ResultList;
import org.linqs.psl.model.term.Variable;
import org.linqs.psl.model.function.ExternalFunction;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.ConstantType;
import org.linqs.psl.model.term.UniqueIntID;
import org.linqs.psl.model.atom.QueryAtom;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.text.similarity.JaroWinklerDistance;

public class SetDiceSimilarity implements ExternalFunction {
	// similarity threshold (default=0.5)
	private double simThresh;

	public SetDiceSimilarity() {
		this.simThresh = 0.5;
	}

	public SetDiceSimilarity(double simThresh) {
		this.simThresh = simThresh;
	}

	@Override
	public int getArity() {
		return 2;
	}

	@Override
	public ConstantType[] getArgumentTypes() {
		return new ConstantType[] {ConstantType.UniqueIntID, ConstantType.UniqueIntID};
	}

	@Override
	public double getValue(ReadableDatabase db, Constant... args) {
		Set<String> authorNamesP1 = new HashSet<String>();
		Set<String> authorNamesP2 = new HashSet<String>();
		StandardPredicate authorOfPredicate = StandardPredicate.get("AuthorOf");
		StandardPredicate authorNamePredicate = StandardPredicate.get("AuthorName");

      DatabaseQuery query = new DatabaseQuery(new QueryAtom(authorOfPredicate, new Variable("A"), args[0]));
		ResultList results = db.executeQuery(query);
		for (int i = 0; i < results.size(); i++) {
			query = new DatabaseQuery(new QueryAtom(authorNamePredicate, results.get(i)[0], new Variable("A")));
			ResultList resultsTwo = db.executeQuery(query);
			for (int j = 0; j < resultsTwo.size(); j++) {
				authorNamesP1.add(String.valueOf(resultsTwo.get(j)[0]));
			}
		}

      query = new DatabaseQuery(new QueryAtom(authorOfPredicate, new Variable("A"), args[1]));
		results = db.executeQuery(query);
      for (int i = 0; i < results.size(); i++) {
			query = new DatabaseQuery(new QueryAtom(authorNamePredicate, results.get(i)[0], new Variable("A")));
			ResultList resultsTwo = db.executeQuery(query);
			for (int j = 0; j < resultsTwo.size(); j++) {
				authorNamesP2.add(String.valueOf(resultsTwo.get(j)[0]));
			}
		}

      if (authorNamesP2.size() < authorNamesP1.size()){
			Set<String> authorNamesTemp = authorNamesP1;
			authorNamesP1 = authorNamesP2;
			authorNamesP2 = authorNamesTemp;
		}
		
		JaroWinklerDistance jaroW = JaroWinklerDistance()
		double total = 0.0;

		for (String s1 : authorNamesP1) {
			double max = 0.0;
			String remove = "";
			for (String s2 : authorNamesP2) {
				double sim = jaroW.score(s1, s2);
				System.out.println("First: " + s1 + " Second: " + s2 + " Jaro: " + sim);
				if (sim > max){
					max = sim;
					remove = s2;
				}
			}
			authorNamesP2.remove(remove);
			total += max;
		}
		return total/(double)authorNamesP1.size();
	}
	/*
	@Override
	public double getValue(ReadableDatabase db, Constant... args) {
		Set<String> authorNamesP1 = new HashSet<String>();
		Set<String> authorNamesP2 = new HashSet<String>();

		StandardPredicate authorOfPredicate = StandardPredicate.get("AuthorOf");
      
      DatabaseQuery query = new DatabaseQuery(new QueryAtom(authorOfPredicate, new Variable("A"), args[0]));
		ResultList results = db.executeQuery(query);
		for (int i = 0; i < results.size(); i++) {
			authorNamesP1.add(String.valueOf(results.get(i)));
		}
		
		query = new DatabaseQuery(new QueryAtom(authorOfPredicate, new Variable("A"), args[1]));
		results = db.executeQuery(query);
		for (int i = 0; i < results.size(); i++) {
			authorNamesP2.add(String.valueOf(results.get(i)));
		}

		int numberOfAuthors = authorNamesP1.size() + authorNamesP2.size();
		if (authorNamesP1.size() > 2 || authorNamesP2.size() > 2){
			System.out.println("Author one: " + authorNamesP1.size() + " Author two: " + authorNamesP2.size());
		}

		// Find the intersection, and get the number of elements in that set.
		authorNamesP1.retainAll(authorNamesP2);
      
		if (authorNamesP1.size() > 0){	
			System.out.println("Common: " + authorNamesP1.size());
		}
      
		int numberOfAuthorsIntersection = authorNamesP1.size();

		// The coefficient is:
		//
		//		  2 ∙ | s1 ⋂ s2 |
		// D = ----------------------
		//		  | s1 | + | s2 |
		//
		double diceSim = (2.0 * (double)numberOfAuthorsIntersection) / (double)numberOfAuthors;

		if (diceSim < simThresh) {
			return 0.0;
		} else {
			return diceSim;
      }
	}*/
}
