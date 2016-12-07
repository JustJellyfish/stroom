/*
 * Copyright 2016 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.index.server.analyzer;

import org.apache.lucene.analysis.Analyzer;

class AlphaAnalyzer extends Analyzer {
    private final boolean caseSensitive;

    AlphaAnalyzer(final boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }

    @Override
    protected TokenStreamComponents createComponents(final String fieldName) {
        if (caseSensitive) {
            return new TokenStreamComponents(new AlphaCSTokenizer());
        }
        return new TokenStreamComponents(new AlphaTokenizer());
    }
}
