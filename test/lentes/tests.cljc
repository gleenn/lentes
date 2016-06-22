(ns lentes.tests
  #?(:cljs
     (:require [cljs.test :as t]
               [clojure.test.check]
               [clojure.test.check.generators :as gen :include-macros true]
               [clojure.test.check.properties :as prop :include-macros true]
               [lentes.core :as l])
     :clj
     (:require [clojure.test :as t]
               [clojure.test.check]
               [clojure.test.check.clojure-test :refer [defspec]]
               [clojure.test.check.generators :as gen]
               [clojure.test.check.properties :as prop]
               [lentes.core :as l]))
  #?(:cljs
     (:require-macros [clojure.test.check.clojure-test :refer (defspec)])))

;; laws

(defn first-lens-law [{:keys [gen lens xgen] :or {xgen gen/any}}]
  (prop/for-all [s gen
                 x xgen]
    (= x
       (l/focus lens (l/put lens x s)))))

(defn second-lens-law [{:keys [gen lens]}]
  (prop/for-all [s gen]
    (= s
       (l/put lens (l/focus lens s) s))))

(defn third-lens-law [{:keys [gen lens xgen] :or {xgen gen/any}}]
  (prop/for-all [s gen
                 a xgen
                 b xgen]
    (= (l/put lens a s)
       (l/put lens a (l/put lens b s)))))

;; generators

(def vector-gen
  (gen/vector gen/any 10))

(def nested-vector-gen
  (gen/vector vector-gen 10))

;; id

(def id-lens
  {:gen gen/any
   :lens l/id})

(defspec id-first-lens-law 10
  (first-lens-law id-lens))

(defspec id-second-lens-law 10
  (second-lens-law id-lens))

(defspec id-third-lens-law 10
  (third-lens-law id-lens))

;; passes

(t/deftest passes
  (let [odd (l/passes odd?)]
    (t/testing "focus"
      (t/is (= 3 (l/focus odd 3)))
      (t/is (nil? (l/focus odd 2))))
    (t/testing "put"
      (t/is (= 42 (l/put odd 42 3)))
      (t/is (= 2 (l/put odd 42 2))))
    (t/testing "over"
      (t/is (= 4 (l/over odd inc 3)))
      (t/is (= 2 (l/over odd inc 2))))))

(t/deftest passes-comp
  (let [fstodd (comp l/fst (l/passes odd?))]
    (t/testing "focus"
      (t/is (= 1 (l/focus fstodd [1 2 3])))
      (t/is (nil? (l/focus fstodd [2 3 4]))))
    (t/testing "put"
      (t/is (= [42 2 3] (l/put fstodd 42 [1 2 3])))
      (t/is (= [2 3 4] (l/put fstodd 42 [2 3 4]))))
    (t/testing "over"
      (t/is (= [2 2 3] (l/over fstodd inc [1 2 3])))
      (t/is (= [2 3 4] (l/over fstodd inc [2 3 4]))))))

;; nth

(defspec nth-first-lens-law 10
  (first-lens-law {:gen vector-gen
                   :lens (l/nth 0)}))

(defspec nth-second-lens-law 10
  (second-lens-law {:gen vector-gen
                    :lens (l/nth 0)}))

(defspec nth-third-lens-law 10
  (third-lens-law {:gen vector-gen
                    :lens (l/nth 0)}))

;;; nth composition

(def ffst (comp l/fst l/fst))

(def ffst-lens
  {:gen nested-vector-gen
   :lens ffst})

(defspec ffst-first-lens-law 10
  (first-lens-law ffst-lens))

(defspec ffst-second-lens-law 10
  (second-lens-law ffst-lens))

(defspec ffst-third-lens-law 10
  (third-lens-law ffst-lens))

;; tail

(def tail-lens
  {:gen vector-gen
   :xgen vector-gen
   :lens l/tail})

(defspec tail-first-lens-law 10
  (first-lens-law tail-lens))

(defspec tail-second-lens-law 10
  (second-lens-law tail-lens))

(defspec tail-third-lens-law 10
  (third-lens-law tail-lens))

;; associative

(defn with-key
  [k]
  (gen/fmap (fn [v]
              {k v})
            gen/any))

(def key-lens
  {:gen (with-key :foo)
   :lens (l/key :foo)})

(defspec key-first-lens-law 10
  (first-lens-law key-lens))

(defspec key-second-lens-law 10
  (second-lens-law key-lens))

(defspec key-third-lens-law 10
  (third-lens-law key-lens))

;; select-keys

(defn with-keys
  [ks]
  (gen/fmap (fn [v]
              (zipmap ks (repeat v)))
            gen/any))

(def select-keys-lens
  {:gen (with-keys [:a :b])
   :xgen (with-keys [:a :b])
   :lens (l/select-keys [:a :b])})

(defspec select-keys-first-lens-law 10
  (first-lens-law select-keys-lens))

(defspec select-keys-second-lens-law 10
  (second-lens-law select-keys-lens))

(defspec select-keys-third-lens-law 10
  (third-lens-law select-keys-lens))

;; in

(def in-lens
  {:gen (gen/fmap (fn [m]
                    (merge m {:a {:b {:c 42}}}))
                  (gen/map gen/keyword gen/any))
   :lens (l/in [:a :b :c])})

(defspec in-first-lens-law 10
  (first-lens-law in-lens))

(defspec in-second-lens-law 10
  (second-lens-law in-lens))

(defspec in-third-lens-law 10
  (third-lens-law in-lens))

;; derived lenses

(defn sec->min
  [sec]
  (/ sec 60))

(defn min->sec
  [min]
  (* min 60))

(def min-lens
  {:gen gen/int
   :xgen gen/int
   :lens (l/units sec->min min->sec)})

(defspec min-first-lens-law 10
  (first-lens-law min-lens))

(defspec min-second-lens-law 10
  (second-lens-law min-lens))

(defspec min-third-lens-law 10
  (third-lens-law min-lens))

;; interop

(t/deftest focus-atom
  (let [source (atom [0 1 2 3 4])
        fsource (l/focus-atom l/fst source)]
    (t/is (= @fsource 0))

    (swap! source #(subvec % 1))
    (t/is (= @source [1 2 3 4]))
    (t/is (= @fsource 1))

    (reset! fsource 42)
    (t/is (= @source [42 2 3 4]))
    (t/is (= @fsource 42))))

(t/deftest focus-atom-watches
  (let [source (atom [0 1 2 3 4])
        watched (volatile! nil)
        fsource (l/focus-atom l/fst source)]
    (add-watch fsource :test (fn [key ref old new]
                               (vreset! watched [ref old new])))

    (swap! source #(subvec % 1))
    (t/is (= @watched
             [fsource 0 1]))

    (swap! fsource inc)
    (t/is (= @watched
             [fsource 1 2]))

    (remove-watch fsource :test)))

#?(:cljs
   (do
     (enable-console-print!)
     (set! *main-cli-fn* #(t/run-tests))))

#?(:cljs
   (defmethod t/report [:cljs.test/default :end-run-tests]
     [m]
     (if (t/successful? m)
       (set! (.-exitCode js/process) 0)
       (set! (.-exitCode js/process) 1))))