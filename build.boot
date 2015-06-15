(set-env!
 :source-paths #{"src" "test"}
 :dependencies '[;Tasks
                 [adzerk/boot-test "1.0.4" :scope "test"]
                 [adzerk/bootlaces "0.1.11" :scope "test"]

                 ; Clojure
                 [org.clojure/clojure "1.6.0"]
                 [org.apache.httpcomponents/httpclient "4.5"] 
                 [clj-oauth "1.5.2"]
                 [prismatic/schema "0.4.3"]
                 [ring/ring-core "1.3.0"]

                 [com.cemerick/friend "0.2.1"]
                 ])


(require '[adzerk.boot-test :refer [test]]
         '[adzerk.bootlaces :refer :all])

(def +version+ "0.1.0")
(bootlaces! +version+) 

(task-options! 
 pom  {:project        'twitter-friend
       :version        +version+
       :description    "Twitter OAuth for Friend"
       :url            "https://github.com/adambard/twitter-friend"
       :scm            {:url "https://github.com/adambard/twitter-friend"}
       :license        {"Eclipse Public License" "http://www.eclipse.org/legal/epl-v10.html"}})
