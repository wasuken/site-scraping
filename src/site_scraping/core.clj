(ns site-scraping.core
  (:gen-class)
  (:require [net.cgrand.enlive-html :as html]
            [org.httpkit.client :as http])
  (:import (java.nio.file Paths)
           (org.apache.lucene.analysis Analyzer TokenStream)
           (org.apache.lucene.analysis.ja JapaneseAnalyzer)
           (org.apache.lucene.analysis.ja.tokenattributes BaseFormAttribute InflectionAttribute PartOfSpeechAttribute ReadingAttribute)
           (org.apache.lucene.analysis.tokenattributes CharTermAttribute)
           (org.apache.lucene.util Version)
           (java.io StringReader)
           (java.util.regex Pattern)))

(def ^Version lucene-version (Version/LUCENE_CURRENT))

;; 与えられた文字列を、形態素解析し単語および属性のマップとして
;; ベクタに含めて返却する
(defn morphological-analysis [^String sentence]
  (let [^Analyzer analyzer (JapaneseAnalyzer. lucene-version)]
    (with-open [^TokenStream token-stream (. analyzer tokenStream
                                             ""
                                             (StringReader. sentence))]

      (let [^CharTermAttribute char-term (. token-stream addAttribute CharTermAttribute)
            ^BaseFormAttribute base-form (. token-stream addAttribute BaseFormAttribute)
            ^InflectionAttribute inflection (. token-stream addAttribute InflectionAttribute)
            ^PartOfSpeechAttribute part-of-speech (. token-stream addAttribute PartOfSpeechAttribute)
            ^ReadingAttribute reading (. token-stream addAttribute ReadingAttribute)]

        (letfn [(create-attributes []
                  {:token (. char-term toString)
                   :reading (. reading getReading)
                   :part-of-speech (. part-of-speech getPartOfSpeech)
                   :base (. base-form getBaseForm)
                   :inflection-type (. inflection getInflectionType)
                   :inflection-form (. inflection getInflectionForm)})]
          (. token-stream reset)

          (try
            (loop [tokenized-seq []]
              (if (. token-stream incrementToken)
                (recur (conj tokenized-seq (create-attributes)))
                tokenized-seq))
            (finally (. token-stream end))))))))



(defn get-dom [url]
  ;; 大量にScrapingするときは５秒にする。
  (Thread/sleep 5000)
  (html/html-snippet (:body @(http/get url {:insecure? true}))))

(defn url-in-page
  "ページ内のurlを取得する"
  [url]
  (remove #(nil? (re-find (re-pattern "^http.*") %))
          (map #(:href (:attrs %))
               (html/select (get-dom url)
                            [:a]))))

(defn get-urls [dom]
  (remove #(nil? (re-find (re-pattern "^http.*") %))
          (map #(:href (:attrs %))
               (html/select dom [:h3.entrylist-contents-title :a]))))

(defn multi-replace
  [s r & patterns]
  (reduce #(.replaceAll (.matcher %2 %1) r) s patterns))

(defn convert-content-zenkaku-csv
  [url]
  (clojure.string/join ","
                       (mapcat #(map (fn [x] (:token x))
                                     (morphological-analysis
                                      (.replaceAll
                                       (.matcher (java.util.regex.Pattern/compile
                                                  "[?!(-~｡-ﾟ \n\t'\")]+") %) "")))
                               (remove #(nil? %)
                                       (map #(:body @(http/get % {:insecure? true}))
                                            (get-urls (get-dom url)))))))

(defn convert-content-csv
  [url]
  (clojure.string/join ","
                       (mapcat #(map (fn [x] (:token x))
                                     (morphological-analysis
                                      (multi-replace % ""
                                                     #"\n"
                                                     #"<script.*?>.*?</script>"
                                                     #"<style.*>.*?</style>"
                                                     #"<.*?>")))
                               (remove #(nil? %)
                                       (map #(:body @(http/get % {:insecure? true}))
                                            (get-urls (get-dom url)))))))

(defn word-count-in-csv
  [words-path]
  (let [words-list (clojure.string/split (slurp words-path) #"," )
        words-cnt-list (frequencies (filter #(> (.length %) 4) words-list))
        words-set (seq (set words-list))]
    (take 100 (into (sorted-map-by (fn [k1 k2] (compare [(get words-cnt-list k2) k2]
                                                        [(get words-cnt-list k1) k1])))
                    words-cnt-list))))

(defn -main
  [& args]
  (letfn []
    (let [url "http://b.hatena.ne.jp/hotentry/it"
          urls (get-urls (get-dom url))
          word-bags '()
          already urls
          cmd (first args)
          words-path "words.txt"]
      (cond (= cmd "csv")
            (spit "words.txt" (convert-content-csv url))
            (= cmd "analy")
            (println (word-count-in-csv words-path))))))
