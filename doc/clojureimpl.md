## Implementing an operation in Koi-clj (in Clojure)

In this document, we'll discuss using koi-clj as a template to implement a new operation in Clojure.

In order to implement a new operation, a developer must 

- Write a Clojure function that satisfies the following constraints

  - It must accept a single argument which is a map
  - the keys must be keywords and the value could be one of a)Starfish Asset b)JSON encoded as Clojure
  - It must return a map as a result. The response map follows the same constraints as the input.

- Define metadata. 

  - The metadata defines the input and outputs, as well as their types. 

Lets look at the implementation of an [iris prediction](). This is an example of a machine learning classifier which is trained on the Iris dataset. The implementation is dummy predictor which takes in an instance from the [iris dataset](https://en.wikipedia.org/wiki/Iris_flower_data_set) and always predicts `setosa` as the predicted class.


### Function that runs the operation.

```clj
(defn predict-class
  [{:keys [dataset]}]
  ;;get the content of the input dataset.
  ;;this would be a test dataset with iris instances
  (let [cont (-> dataset s/content s/to-string)
        isp (clojure.string/split cont #"\n" )
        ;;create predictions, which adds the value for predicted class
         predictions (->> (into [(str (first isp) ",predclass")]
                                 (mapv #(str % ",setosa") (rest isp)))
                           (clojure.string/join "\n"))
         ;;return a map with the parametername (predictions) and 
         ;;an asset contains the content with a new column
         res {:predictions (s/asset (s/memory-asset
                                      {"iris" "predictions"}
                                      predictions))}]
      res))
```

### Define metadata 

This metadata helps the middleware to test if

- The input and output arguments are valid
- If the arguments are references to asset ids, the middleware will retrieve and create them (as Starfish assets)
- It does the same with the response

Example of metadata for this operation:

```clj
  ;;the key in the operation-registry map identifying the operation with a simple name
  :iris-predictor
  ;;the map value contains the configuration required
  {
  ;;the class name and the function
  :handler "koi.examples.predict-iris/predict-class"
   :metadata
   {:operation
    {
	;;the input parameters
	:params {:dataset {:type "asset", :position 0, :required true}}
	;;which modes are supported
     	:modes [:sync :async]
	;;the result parameters
     	:results {:predictions {:type "asset", :required true}}}}}

```

### Adding it to the list of operations.

`config.edn` maintains a list of operations. On startup, Koi will

- registers the metadata for each operation using an Agent. 
- register a rest endpoint corresponding to the ID of the operation. 
