(ns clj-bandit.ring.example-app
  (:use [compojure.core]
        [hiccup.core]
        [hiccup.page]
        [ring.middleware stacktrace reload]
        [ring.util.response]
        [ring.adapter.jetty :only (run-jetty)]
        [clj-bandit.arms :only (mk-arms reward pulled)]
        [clj-bandit.algo.ucb :only (select-arm)]))

(defonce bandit (ref (mk-arms :advert1 :advert2 :advert3)))

(defmulti advertisement :name)
(defmethod advertisement :advert1
  [_]
  (html [:div.advert
         [:h3 "Advert 1"]
         [:a {:href "/click/advert1"} "Buy Now"]]))
(defmethod advertisement :advert2
  [_]
  (html [:div.advert
         [:h3 "Advert 2"]
         [:a {:href "/click/advert2"} "More Info"]]))
(defmethod advertisement :advert3
  [_]
  (html [:div.advert
         [:h3 "Advert 3"]
         [:a {:href "/click/advert3"} "Apply Now"]]))

(defn record-pull
  [arm-state {:keys [name] :as arm}]
  (update-in arm-state [name] pulled))

(defn record-click
  [arm-state arm-name]
  (update-in arm-state [arm-name] reward 1))

(defn advert-html
  "Uses the bandit algorithm to optimise which advert to show
   and returns it's HTML."
  []
  (dosync
   (let [pulled (select-arm (vals @bandit))]
     (alter bandit record-pull pulled)
     (advertisement pulled))))

(defn bandit-perf
  []
  [:div#arms
   [:h2 "Bandit Numbers"]
   [:table
    [:thead
     [:tr
      [:th "Arm"]
      [:th "Pulls"]
      [:th "Current Value"]]]
    [:tbody
     (for [{:keys [name pulls value]} (vals @bandit)]
       [:tr
        [:td name]
        [:td pulls]
        [:td value]])]]])

(defn layout
  [& body]
  (html5
   [:head [:title "clj-bandit sample"]]
   [:body
    [:h1 "Bandit Testing"]
    body
    (bandit-perf)]))

(defroutes main-routes
  (GET "/" []
       (layout
        [:div#main
         (advert-html)]))
  (GET "/click/:arm-name" [arm-name]
       (dosync
        (alter bandit record-click (keyword arm-name)))
       (redirect "/")))

(def app (-> main-routes
             (wrap-reload '(clj-bandit.ring example-app))
             (wrap-stacktrace)))

(defn -main
  []
  (run-jetty #'app {:port 8080}))