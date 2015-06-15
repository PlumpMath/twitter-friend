# Twitter OAuth support for Friend

`twitter-friend` is a library to provide a ready-to-use
[friend](https://github.com/cemerick/friend) workflow for twitter.


## Installation

Add `[twitter-friend "0.1.0"]` to your leiningen or boot dependencies.

## Usage

Twitter uses three-legged oauth, which involves two steps to authenticate
the user:

* The user heads to your `:login-uri`, and gets redirected to Twitter to authorize
  your access.
* After authenticating the user, twitter redirects back to your application.

This library attempts to keep the choices you have to make to a minimum:

1. You have to provide the URL that twitter redirects to after authorizing.
2. You have to provide a `credential-fn` that accepts a twitter access token
   and returns a friend authorization map.

The credential function receives a map that looks like this:

```
{:oauth_token "users-twitter-access-token"
 :oauth_token_secret "users-twitter-access-secret"
 :user_id "users-twitter-user-id"
 :screen_name "users-twitter-screen-name"}
```

## Example Usage

```
(require '[twitter-friend :refer [twitter-auth-workflow]])


;; Your credential function is called when the authorization flow
;; is finished.
(defn my-credential-fn [{:keys [oauth_token
                                oauth_token_secret
                                user_id
                                screen_name] :as auth-response}]
  (let [user (mydb/get-or-create-user auth-response)]
    {:identity (:id user) :roles #{::user} :user user}))


(def my-workflow
  (twitter-auth-workflow-config
    {:consumer-key "MY_TWITTER_CONSUMER_KEY"
    :consumer-secret "MY_TWITTER_CONSUMER_SECRET"
    :oauth-callback-uri {:base-url "http://myapp.com"
                         :path "/path/to/oauth"}
    :credential-fn my-credential-fn}))


(def myapp
  (-> myroutes
      (friend :workflows [my-workflow])
      (wrap-params)
      (wrap-keyword-params)
      (wrap-session)))  ; These middlewares are required

```


## Workflows other than redirect-with-session

If you don't want your app to redirect automatically to twitter, or to use
the session middleware (for example, if you're integrating oauth with an ajax
call), you can additionally provide two more configuration
keys to `twitter-auth-workflow:

`authorization-fn` must be a function of three arguments that accepts a `request-token` object, 
the authorization redirect url, and the incoming Ring request, and returns a ring response. You'll need to
eventually redirect your user to the auth-url, and store the `request-token` in such
a way that you can retrieve it later.

`request-token-fn` accepts a request, and returns the `request-token` that you
stored earlier.

Here are the default implementations, which use ring' session middleware, for reference:

```clojure
(defn default-authorization-fn [request-token auth-url req]
 (update-in
     (resp/redirect auth-url)
     [:session] assoc :oauth-request-token request-token))


(defn default-request-token-fn [req]
  (get-in req [:session :oauth-request-token]))
```

You can pass these in as additional keys in the config map passed to twitter-auth-workflow.
