(ns clj-bandit.simulate
  (:use [clojure.data.csv :only (write-csv)]
        [clojure.java.io :only (writer)]
        [clj-bandit.epsilon]
        [clj-bandit.storage :only (atom-storage)]
        [clj-bandit.core :only (select-arm update-reward cumulative-sum)]))

(defn bernoulli-arm [p] (fn [] (if (> (rand) p) 0 1)))

(defn draw-arm [f] (f))

;; take 5 results from a sequence of results for a bernoulli arm
;; (take 5 (repeatedly #(draw-arm (bernoulli-arm 0.1))))

(defn arm-name
  [pull]
  (first (keys (:arm pull))))

(defn simulation-results
  [{:keys [algo-name algo variant] :as simulation} iterations simulation-number]
  (let [arm-labels [:arm1 :arm2 :arm3 :arm4 :arm5]
        arms (map bernoulli-arm [0.1 0.1 0.1 0.1 0.9])]
    (let [rows (map (fn [t]
                      (let [chosen-arm (arm-name (select-arm algo))
                            reward (draw-arm (nth arms (.indexOf arm-labels chosen-arm)))
                            cumulative-reward 0]
                        (update-reward algo chosen-arm reward)
                        [algo-name variant simulation-number t chosen-arm reward]))
                    (range 1 iterations))
          cumulative-rewards (cumulative-sum (map last rows))]
      (map conj rows cumulative-rewards))))

(defn mk-epsilon-algorithm
  [epsilon]
  {:algo-name "epsilon-greedy"
   :variant epsilon
   :algo (epsilon-greedy-algorithm epsilon (atom-storage (mk-arms [:arm1 :arm2 :arm3 :arm4 :arm5])))})

(defn run-simulation
  ([]
     (run-simulation 1000 200))
  ([simulations iterations]
     (with-open [csv (writer "tmp/results.csv")]
       (let [epsilon-algos (map mk-epsilon-algorithm [0.1 0.2 0.3 0.4 0.5])
             algorithms epsilon-algos
             results (doall (apply concat (map (fn [{:keys [algo-name variant] :as algorithm}]
                                                 (println "Testing" algo-name variant)
                                                 (apply concat (map (partial simulation-results algorithm (inc iterations))
                                                                    (range 1 (inc simulations)))))
                                               algorithms)))]
         (write-csv csv results)))))
