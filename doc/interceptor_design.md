## Interceptor design

A new operation implementation accepts a Clojure map as input, and return the same as the output. The map can contain Starfish Asset as values. 

The DEP6 API spec is a REST API that accepts JSON. In order to connect the REST API with the operation implementation, Koi uses a set of interceptors.

Interceptors are an alternative to middleware. [Here's](https://quanttype.net/posts/2018-08-03-why-interceptors.html) a longer introduction to interceptors.

Koi uses interceptors for the following tasks:

1. (Input) Parameter validation: The operation schema defines the keys in the input argument map. This function validates if the inputs have the required keys (and valid values against those keys). Implemented by the `param-validator`
2. (Output) Result validation: the same as the above. Implemented by `result-validator`.
3. Asset retrieval: The implmenting function requires Asset objects. This interceptor takes the asset id and creates an Asset object using a preconfigured Agent. Implemented by the `input-asset-retrieval` interceptor.
4. Asset storage: The implementing function returns Asset objects, which need to be stored by a preconfigured Agent, and referenced by an Asset id in the DEP6 API spec. This is done by the `output-asset-upload` interceptor.
5. Provenance: The metadata for all generated assets must include the input assets as dependencies. This is performed jointly by the `input-asset-retrieval` interceptor (which stores the input assets) and the `output-asset-upload` which adds provenance metadata based on the assets passed in the input.
6. Error handling: When any of the interceptors or the operation implementation throws an exception, the `wrap-error-cause` interceptor consumes the exception and returns map with the exception message against the `:cause` key.
