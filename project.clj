(defproject site-scraping "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [http-kit "2.2.0"]
                 [enlive "1.1.6"]
                 [org.apache.lucene/lucene-kuromoji "3.6.2"]
                 [incanter "1.9.3"]]
  :main site-scraping.core)
