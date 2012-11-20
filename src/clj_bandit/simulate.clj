(ns clj-bandit.simulate
  (:use [clojure.data.csv :only (write-csv)]
        [clojure.java.io :only (writer)]
        [clj-bandit.algo.epsilon :only (epsilon-greedy-algorithm)]
        [clj-bandit.algo.softmax :only (softmax-algorithm)]
        [clj-bandit.algo.ucb :only [ucb-algorithm]]
        [clj-bandit.storage :only (atom-storage)]
        [clj-bandit.core :only (mk-arms select-arm update-reward cumulative-sum anneal)]))

(defn bernoulli-arm [p] (fn [] (if (> (rand) p) 0 1)))

(defn draw-arm [f] (f))

;; take 5 results from a sequence of results for a bernoulli arm
;; (take 5 (repeatedly #(draw-arm (bernoulli-arm 0.1))))

(defn arm-name
  [pull]
  (first (keys pull)))

(defn simulation-results
  [{:keys [algo-name algo-fn variant parameter] :as algorithm} iterations simulation-number]
  (let [arm-labels [:arm1 :arm2 :arm3 :arm4 :arm5]
        arms (map bernoulli-arm [0.1 0.1 0.1 0.1 0.9])
        algo (algo-fn)]
    (let [rows (map (fn [t]
                      (let [chosen-arm (arm-name (select-arm algo))
                            reward (draw-arm (nth arms (.indexOf arm-labels chosen-arm)))
                            cumulative-reward 0]
                        (update-reward algo chosen-arm reward)
                        [algo-name variant parameter simulation-number t chosen-arm reward]))
                    (range 1 iterations))
          cumulative-rewards (cumulative-sum (map last rows))]
      (map conj rows cumulative-rewards))))

(defn mk-storage
  []
  (atom-storage (mk-arms [:arm1 :arm2 :arm3 :arm4 :arm5])))

(defn mk-epsilon-algorithm
  [type epsilon]
  {:algo-name "epsilon-greedy"
   :parameter (if (= "standard" type) epsilon)
   :variant type
   :algo-fn (fn [] (epsilon-greedy-algorithm epsilon (mk-storage)))})

(defn mk-softmax-algorithm
  [type temperature]
  {:algo-name "softmax"
   :parameter (if (= "standard" type) temperature)
   :variant type
   :algo-fn (fn [] (softmax-algorithm temperature (mk-storage)))})

(defn run-simulation
  ([]
     (run-simulation 1000 200))
  ([simulations iterations]
     (with-open [csv (writer "tmp/results.csv")]
       (let [epsilon-algos (concat (map (partial mk-epsilon-algorithm "standard") [0.1 0.2 0.3 0.4 0.5])
                                   (map (partial mk-epsilon-algorithm "anneal") [anneal]))
             softmax-algos (map (partial mk-softmax-algorithm "standard") [0.1 0.2 0.3 0.4 0.5])
             ucb-algo {:algo-name "ucb" :variant "N/A" :algo-fn (fn [] (ucb-algorithm (mk-storage)))}
             algorithms (concat [ucb-algo] epsilon-algos softmax-algos)]
         (write-csv csv (apply concat (map (fn [algorithm]
                                             (apply concat (pmap (partial simulation-results algorithm (inc iterations))
                                                                 (range 1 (inc simulations)))))
                                           algorithms)))))))
