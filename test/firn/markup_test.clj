(ns firn.markup-test
  (:require [firn.markup :as sut]
            [clojure.test :as t]))


;; Mocks


(def sample-links {:file-img  {:type "link", :path "file:test-img.png"}
                   :file-link {:type "link", :path "file:file2.org", :desc "File 2"}
                   ;; :string-img {:type "link", :path "./test-img.png"} ;; not supported yet.
                   :img-rel   {:type "link", :path "./assets/images/test-img.png"}
                   :file-img2 {:type "link", :path "file:media/test-img.png"}
                   :http-img  {:type "link",
                               :path "https://www.fillmurray.com/g/200/300.jpg",
                               :desc "Miscellaneous Features :: CIDER Docs"}
                   :http-link {:type "link",
                               :path "https://docs.cider.mx/cider/usage/misc_features.html",
                               :desc "Miscellaneous Features :: CIDER Docs"}})

;; Tests

(t/deftest link->html
  (t/testing "http-link"
    (t/is (= (sut/link->html (sample-links :http-link))
             [:a
              {:href "https://docs.cider.mx/cider/usage/misc_features.html"}
              "Miscellaneous Features :: CIDER Docs"])))


  (t/testing "http-image-link"
    (t/is (= (sut/link->html (sample-links :http-img))
             [:img
              {:src "https://www.fillmurray.com/g/200/300.jpg"}])))


  (t/testing "img-link"
    (t/is (= (sut/link->html (sample-links :file-img))
             [:img {:src "./test-img.png"}])))

  (t/testing "img-rel"
    (t/is (= (sut/link->html (sample-links :img-rel))
             [:img {:src "./assets/images/test-img.png"}])))


  (t/testing "img-link at another path"
    (t/is (= (sut/link->html (sample-links :file-img2))
             [:img {:src "./media/test-img.png"}])))

  (t/testing "internal-link"
    (t/is (= (sut/link->html (sample-links :file-link))
             [:a {:href "./file2"} "File 2"]))))
