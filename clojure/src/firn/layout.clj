(ns firn.layout
  "Namespace responsible for using layouts.
  Layouts enable users to have custom layouts for the static site generators.
  This occurs by slurping in some layout files -- which are just `.clj` files
  And then applying them inline."

  (:require [firn.markup :as markup]
            [firn.org :as org]
            [hiccup.core :as h]
            [firn.file :as file]
            [firn.util :as u]))

(defn internal-default-layout
  "The default template if no `layout` key and no default.clj layout is specified."
  [{:keys [render] :as data}]
  [:html
   [:head
    [:meta {:charset "UTF-8"}]
    [:link {:rel "stylesheet" :href "/static/css/firn_base.css"}]]
   [:main
    [:div (render :toc)]
    [:div (render :file)]]])

(defn get-layout
  "Checks if a layout for a project exists in the config map
  If it does, return the function value of the layout, otherwise the default template "
  [config file layout]
  (let [curr-file-name (file :name)
        file-layout    (get-in config [:layouts layout])
        default-layout (-> config :layouts :default)]
    (cond
      (not (nil? file-layout))
      file-layout

      (not (nil? default-layout))
      default-layout

      :else
      (do
        (if layout
          (println "\n⚠ File:" curr-file-name "says it uses a layout of" layout "but no corresponding layout file exists in _firn/layouts")
          (println "\n⚠ File:" curr-file-name "does not have #+FIRN_LAYOUT key and no default layout file was found."))
        (println "☝ Resorting to internal template!\n")
        internal-default-layout))))

(defn render
  "Renders something from your org-mode file.
  This would be a nice multi-method if we could find a way
  to partially apply the file map to it."
  ([partial-map action]
   (render partial-map action {}))
  ([partial-map action opts]
   (let [{:keys [file config]}     partial-map
         org-tree                  (file :as-edn)
         config-settings           (config :user-config)                       ; site-wide config: 0 precedence
         site-map                  (config :site-map)
         front-matter-settings     (when (seq file) (-> file :meta :keywords)) ; file-setting config: 2 precedence
         layout-settings           (if (map? opts) opts {})
         ;; the big merged options! This is used across various render fns,
         ;; essentially a refined config object with user specific (and some
         ;; internal data)
         merged-options            (merge config-settings layout-settings front-matter-settings {:site-links-private (config :site-links-private)})
         cached-sitemap-html       (atom nil)
         is-headline?              (string? action)
         {:keys [toc logbook
                 firn-under
                 firn-order
                 date-created-ts]} (file :meta)]

     ;; cache the site-map if it's not there already
     (when-not @cached-sitemap-html
       (reset! cached-sitemap-html (markup/render-site-map site-map opts)))

     (cond
       ;; render the whole file.
       (= action :file)
       (markup/to-html (file :as-edn) merged-options)

       ;; render just the content of a headline.
       (and is-headline? (opts :exclude-headline?))
       (let [headline-content (org/get-headline-content org-tree action)]
         (markup/to-html headline-content merged-options))

       ;; render a heading (title and content).
       (and is-headline?)
       (markup/to-html (org/get-headline org-tree action) merged-options)

       ;; render a polyline graph of the logbook of the file.
       (= action :logbook-polyline)
       (org/poly-line logbook opts)

       ;; Render the sitemap; cache it the first time it runs
       (and (= action :sitemap) (seq site-map))
       (if-not @cached-sitemap-html
         (do (reset! cached-sitemap-html (markup/render-site-map site-map opts))
             @cached-sitemap-html)
         @cached-sitemap-html)

       ;; render breadcrumbs
       (= action :breadcrumbs)
       (markup/render-breadcrumbs firn-under site-map opts)

       ;; render a list of links that link back to the current file
       (= action :backlinks)
       (markup/render-backlinks {:site-links         (config :site-links)
                                 :site-links-private (config :site-links-private)
                                 :file               file
                                 :site-url           (get-in config [:user-config :site-url])})

       ;; render the previous file based on firn-order
       (= action :adjacent-files)
       (markup/render-adjacent-file
        (merge
         {:sitemap         site-map
          :firn-under      firn-under
          :firn-order      firn-order
          :date-created-ts date-created-ts}
         (select-keys opts [:prev-text :next-text :order-by :as-data])))

       ;; render a list of all file tags across the site.
       (= action :firn-tags)
       (markup/render-firn-tags (config :firn-tags) opts)

       (= action :firn-file-tags)
       (markup/render-firn-file-tags (file/get-firn-tags file) merged-options)

       (= action :related-files)
       (markup/render-related-files (-> file :meta :title)
                                    (file/get-firn-tags file)
                                    (config :firn-tags))

       ;; render a list of org tags
       (= action :org-tags)
       (markup/render-org-tags (config :org-tags) opts)

       ;; render a table of contents
       (= action :toc)
       (let [toc  toc ; get the toc for the file.
             ;; get configuration for toc in order of precedence
             opts (merge (config-settings :firn-toc)
                         layout-settings
                         (front-matter-settings :firn-toc))]
         (when (seq toc)
           (markup/make-toc toc opts)))

       :else ; error message to indicate incorrect use of render.
       (str "<div style='position: fixed; background: antiquewhite; z-index: 999; padding: 24px; left: 33%; top: 33%; border: 13px solid lightcoral; box-shadow: 3px 3px 3px rgba(0, 0, 0, 0.3);'>"
            "<div style='text-align: center'>Render Error.</div>"
            "<div>Incorrect use of `render` function in template:
                <br> action: => " action " <code> << is this a valid value? </code>
                <br> opts:  => " opts " <code> << is this a valid value? </code>"
            "<br></div> "
            "</div>")))))

(defn build-url
  "returns a helper function for use in layouts for easier building of urls from
  site-url"
  [site-url]
  (fn [& args] (apply str site-url args)))

(defn prepare
  "Prepare functions and data to be available in layout functions.
  This is a 'public api' that a user would 'invoke' for common rendering tasks
  made available in user layouts.
  NOTE | PERF:  This might be being called twice."
  [config file]
  (let [site-url (-> config :user-config :site-url)]
    {;; Layout stuff --
     :render        (partial render {:file file :config config})
     :partials      (config :partials)
     ;; Site-side stuff --
     :site-map      (config :site-map)
     :site-links    (config :site-links)
     :site-logs     (config :site-logs)
     :site-title    (-> config :user-config :site-title)
     :site-author   (-> config :user-config :site-author)
     :site-desc     (-> config :user-config :site-desc)
     :site-url      site-url
     :org-tags      (config :org-tags)
     :firn-tags     (config :firn-tags)
     :build-url     (build-url site-url)
     :config        config
     ;; File wide meta --
     :file          file
     :meta          (file :meta)
     :logbook       (-> file :meta :logbook)
     :file-links    (-> file :meta :links)
     :title         (-> file :meta :title)
     :firn-under    (-> file :meta :firn-under)
     :logbook-total (-> file :meta :logbook-total)
     :date-updated  (-> file :meta :date-updated)
     :date-created  (-> file :meta :date-created)}))

(defn apply-layout
  "If a file has a template, render the file with it, or use the default layout"
  [config file layout]
  (let [selected-layout (get-layout config file layout)]
    (h/html (selected-layout (prepare config file)))))
