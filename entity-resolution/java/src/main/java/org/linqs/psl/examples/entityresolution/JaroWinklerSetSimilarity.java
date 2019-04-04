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
package org.linqs.psl.examples.entityresolution;

import org.linqs.psl.database.DatabaseQuery;
import org.linqs.psl.database.ReadableDatabase;
import org.linqs.psl.database.ResultList;
import org.linqs.psl.model.atom.QueryAtom;
import org.linqs.psl.model.function.ExternalFunction;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.ConstantType;
import org.linqs.psl.model.term.Variable;

import org.apache.commons.text.similarity.JaroWinklerDistance;

import java.util.HashSet;
import java.util.Set;

public class JaroWinklerSetSimilarity implements ExternalFunction {
    @Override
    public int getArity() {
        return 2;
    }

    @Override
    public ConstantType[] getArgumentTypes() {
        return new ConstantType[] {ConstantType.UniqueIntID, ConstantType.UniqueIntID};
    }

   private Set<String> loadSets(ReadableDatabase db, Constant arg){
        Set<String> authorSet = new HashSet<String>();
        StandardPredicate authorOfPredicate = StandardPredicate.get("AuthorOf");
        StandardPredicate authorNamePredicate = StandardPredicate.get("AuthorName");

        DatabaseQuery query = new DatabaseQuery(new QueryAtom(authorOfPredicate, new Variable("A"), arg));
        ResultList results = db.executeQuery(query);
        for (int i = 0; i < results.size(); i++) {
            query = new DatabaseQuery(new QueryAtom(authorNamePredicate, results.get(i)[0], new Variable("A")));
            ResultList resultsTwo = db.executeQuery(query);
            for (int j = 0; j < resultsTwo.size(); j++) {
                authorSet.add(String.valueOf(resultsTwo.get(j)[0]));
            }
        }
        return authorSet;
    }

    @Override
    public double getValue(ReadableDatabase db, Constant... args) {
        Set<String> authorNamesP1 = loadSets(db, args[0]);
        Set<String> authorNamesP2 = loadSets(db, args[1]);

        if (authorNamesP2.size() < authorNamesP1.size()){
            Set<String> authorNamesTemp = authorNamesP1;
            authorNamesP1 = authorNamesP2;
            authorNamesP2 = authorNamesTemp;
        }

        double total = 0.0;
        double max = 0.0;
        double sim = 0.0;
        JaroWinklerDistance jaroW = new JaroWinklerDistance();

        // Does a pair wise comparison, greedly takes the largest Jaro Winkler similarity, and goes to the next element.
        for (String s1 : authorNamesP1) {
            String remove = null;
            max = 0.0;
            for (String s2 : authorNamesP2) {
                sim = jaroW.apply(s1, s2);
                if (remove == null || sim > max) {
                    max = sim;
                    remove = s2;
                }
            }
            authorNamesP2.remove(remove);
            total += max;
        }
        return total / (double)authorNamesP1.size();
    }
}
