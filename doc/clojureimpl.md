## Implementing an operation in Clojure

In this document, we'll discuss using koi-clj as a template to implement a new operation in Clojure.

In order to create a new operation, a developer must 

- implement the protocols declared [protocols.clj](https://github.com/DEX-Company/koi-clj/blob/develop/src/koi/protocols.clj)
- create the metadata, which is discussed in detail [here](#) 

The protocol declares three methods

* `invoke-sync` This method is called for the synchronous invocation of an operation. 
* `invoke-async` This method is called for the asynchronous invocation of an operation. It must return immediately, and return a job id.
* `get-params` This method returns the metadata (input and output) for the operation.

Lets look at the implementation of an [iris predictor](https://github.com/DEX-Company/koi-clj/blob/develop/src/koi/examples/predict_iris.clj). This is an example of a machine learning classifier which is trained on the Iris dataset. The implementation is dummy predictor which takes in an instance from the [iris dataset](https://en.wikipedia.org/wiki/Iris_flower_data_set) and always predicts `setosa` as the predicted class.

### Creating a deftype 

First, we need to create a [deftype](https://clojuredocs.org/clojure.core/deftype) that implements the protocol abstractions.


Note that:

-  the `invoke-*` methods call the *process* function (explained later)
- `get-params` is a clojure.spec compliant definition of the inputs

```clj

(deftype PredictIrisClass [jobs jobids]

  prot/PSyncInvoke
  (invoke-sync [_ args]
    (process args predict-class))

  prot/PAsyncInvoke
  (invoke-async [_ args]
    (async-handler jobids jobs #(process args predict-class)))

  prot/PParams
  (get-params [_]
    ::params))

```

### Function that runs the operation.

```clj
(defn predict-class
  [agent {:keys [dataset]}]
  ;;get the content of the input dataset.
 ;;this would be a test dataset with iris instances

  (let [ast (get-asset agent dataset)
        cont (-> ast s/content s/to-string)]
    ;;this should return a function
    (fn []
      (let [isp (clojure.string/split cont #"\n" )
      ;;create predictions, which adds the value for predicted class
            predictions (->> (into [(str (first isp) ",predclass")]
                       (mapv #(str % ",setosa") (rest isp)))
                 (clojure.string/join "\n"))
                 ;;;return a map with the parametername (predictions) and the content
            res {:results [{:param-name :predictions
                            :type :asset
                            :content predictions}]}]
        ;(info " result of predict-class " res)
        res))))
```

### Adding it to the list of operations.

`op_handler.clj` maintains a list of operations. On Koi startup, the operation handler

- registers the metadata for each operation using an Agent. 
- registers a rest endpoint corresponding to the ID of the operation. 


The complete source can be found [here](https://github.com/DEX-Company/koi-clj/blob/develop/src/koi/examples/predict_iris.clj)
