(ns twitter-friend-test
  (:require [clojure.test :refer [is deftest]]
            [cemerick.friend :as friend]
            [oauth.client :as oauth]
            [ring.util.request :as request]
            [ring.middleware.session :refer [wrap-session]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [twitter-friend :refer [twitter-auth-workflow]]

            ))

(defmacro with-test-redefs [expected-request-token
                       expected-request-secret
                       expected-access-token
                       expected-access-secret
                       & body
                       ]
  `(with-redefs
     [oauth/request-token (fn [& args#] {:oauth_token ~expected-request-token
                                        :oauth_token_secret ~expected-request-secret
                                        :oauth_callback_confirmed "true"})
      oauth/access-token (fn [& args#] {:oauth_token ~expected-access-token
                                       :oauth_token_secret ~expected-access-secret
                                       })
      ]
     ~@body
     ))

(deftest test-basic-operation
  (with-test-redefs "tok" "sec" "HEY HEY HEY" ""
    (let [login-uri "/sign-in"
          workflow (twitter-auth-workflow
                     {:consumer-key ""
                      :consumer-secret ""
                      :oauth-callback-uri {:base-url "http://example.com"
                                           :path "/oauth/callback"}
                      :credential-fn (fn [access-token] {:identity (:oauth_token access-token)
                                                         :resp access-token
                                                         :roles #{::user}
                                                         :complete "HECKYES"})}
                     )
          handler (-> (fn [req] {:body req :status 200 :headers {}})
                      (friend/authenticate {:workflows [workflow]
                                            :login-uri login-uri
                                            })
                      (wrap-keyword-params)
                      (wrap-params)
                      )
          req {:server-port 80
               :scheme "http"
               :server-name "example.com"
               :request-method :get
               :headers {}
               :session {}
               :query-string ""}
          login-resp (handler (assoc req :uri login-uri :query-string ""))
          ]

      (is (= (-> (handler req) :session ::friend/identity :current) nil))
      (is (= (-> login-resp :headers (get "Location"))
             "https://api.twitter.com/oauth/authorize?oauth_token=tok"))
      (is (= (-> (handler (assoc req
                                 :uri "/oauth/callback"
                                 :query-string "&oauth_verifier=veri"
                                 :session (:session login-resp)))
                 :session ::friend/identity :current) "HEY HEY HEY")))))

(deftest test-with-auth-and-req-tok-fns
  (with-test-redefs "req-tok" "req-sec" "acc-tok" "acc-sec"
  (let [request-token (atom nil)
        login-uri "/signonin"
        conf {:consumer-key ""
              :consumer-secret ""
              :oauth-callback-uri {:base-url "http://example.com"
                                   :path "/oauth/callback"}
              :credential-fn #(hash-map :identity (:oauth_token %)
                                        :resp %
                                        :roles #{::user}
                                        :complete "HECKYES")
              :authorization-fn (fn [it auth-url _]
                                  (reset! request-token it)
                                  {:body auth-url :status 200 :headers {}}
                                  )
              :request-token-fn (fn [_] @request-token)}
        workflow (twitter-auth-workflow conf)
        handler (-> (fn [req] {:body req :status 200 :headers {}})
                    (friend/authenticate {:workflows [workflow]
                                          :login-uri login-uri
                                          })
                    (wrap-keyword-params)
                    (wrap-params)
                    )
        req {:server-port 80
             :scheme "http"
             :server-name "example.com"
             :request-method :get
             :headers {}
             :session {}
             :query-string ""}
        login-resp (handler (assoc req :uri login-uri :query-string ""))
        ]
    (is (= @request-token {:oauth_token "req-tok"
                            :oauth_token_secret "req-sec"
                            :oauth_callback_confirmed "true"}))
    (is (= "https://api.twitter.com/oauth/authorize?oauth_token=req-tok"
           (-> login-resp :body)))
    (is (= "acc-tok"
           (-> (handler (assoc req
                               :uri "/oauth/callback"
                               :query-string "&oauth-verifier=veri"))
               :session ::friend/identity :current))))))

