(ns twitter-friend
  (:require
    [cemerick.friend :as friend]
    [cemerick.friend.workflows :refer [make-auth]]
    [oauth.client :as oauth]
    [ring.util.response :as resp]
    [ring.util.request :refer [path-info]]
    [schema.core :as s]
    ))


(defn- build-consumer [consumer-key consumer-secret]
  (oauth/make-consumer
    consumer-key
    consumer-secret
    "https://api.twitter.com/oauth/request_token"
    "https://api.twitter.com/oauth/access_token"
    "https://api.twitter.com/oauth/authorize"
    :hmac-sha1))


(defn default-authorization-fn [request-token auth-url req]
 (update-in
     (resp/redirect auth-url)
     [:session] assoc :oauth-request-token request-token))


(defn default-request-token-fn [req]
  (get-in req [:session :oauth-request-token]))


(defn- handle-authorization [consumer authorization-fn oauth-callback-uri req]
 (let [request-token (oauth/request-token consumer
                                          (str (:base-url oauth-callback-uri)
                                               (:path oauth-callback-uri)))
       approval-uri (oauth/user-approval-uri consumer (:oauth_token request-token))]
   (authorization-fn request-token
                     approval-uri
                     req
                     )))


(defn- handle-oauth-callback [consumer request-token-fn credential-fn req]
  (when-let [request-token (request-token-fn req)]
    (when-let [access-token (oauth/access-token consumer request-token (get-in req [:params :oauth_verifier]))]
      (make-auth (credential-fn access-token)))))


(s/defschema TwitterAuthConfig {:consumer-key String
                                :consumer-secret String
                                :oauth-callback-uri {:base-url String
                                                     :path String}
                                (s/optional-key :credential-fn) s/Any
                                (s/optional-key :authorization-fn) s/Any
                                (s/optional-key :request-token-fn) s/Any
                                })

(s/defn ^:always-validate twitter-auth-workflow
  "Build a consumer workflow for twitter sign-in using Oauth1.
  :consumer-key and :consumer-secret are your twitter credentials.
  :oauth-callback-uri is a map with two keys, :path and :base-url. Both must be present.

  Example useage:

  (def my-workflow
    (twitter-auth-workflow :consumer-key \"TWITTER_CONSUMER_KEY\"
                           :consumer-secret \"TWITTER_CONSUMER_SECRET\"
                           :oauth-callback-uri {:base-url \"http://myhost\"
                                                :path \"/my/path/to/oauth/callback\"}))

  (defn my-credential-fn [access-token]
    {:identity access-token :roles #{::user}})}))

  (friend/authenticate my-handler
                       {:workflows [my-workflow]
                        :credential-fn my-credential-fn)
  "
  [config :- TwitterAuthConfig]
  (let [{:keys [consumer-key
                consumer-secret
                oauth-callback-uri
                credential-fn
                authorization-fn
                request-token-fn]} config
        consumer (build-consumer consumer-key consumer-secret) 
        ]
    (fn [req]
      (let [credential-fn (or credential-fn (get-in req [::friend/auth-config :credential-fn]))
            authorization-fn (or authorization-fn default-authorization-fn)
            request-token-fn (or request-token-fn default-request-token-fn)]

        (cond
          ; Oauth callback
          (= (path-info req) (:path oauth-callback-uri) )
          (handle-oauth-callback consumer request-token-fn credential-fn req)

          ; Login url
          (= (path-info req) (get-in req [::friend/auth-config :login-uri]))
          (handle-authorization consumer authorization-fn oauth-callback-uri req))))))
