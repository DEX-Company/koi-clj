# Koi-clj 

Koi-clj is a reference implementation of the DEP6 API. It also provides a framework for Clojure developers to implement new operations and deploy them.

Koi serves the following use cases:

1. As a test endpoint, it enables consumers of the data ecosystem to invoke test operations on their assets. 
2. For developers familiar with Clojure, it provides them with the tooling to quickly implement new operations. [This document](clojureimpl.md) describes the process to create a new operation.

The design for Koi-clj is described in [this document](interceptor_design.md).
