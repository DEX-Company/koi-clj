# Koi-clj 

A reference implementation of Invokable services implemented in Clojure. 


## Table of Contents

* [Overview](#overview)
* [Running locally](#running-locally)
* [Configuration](#configuration)
* [Documentation](#documentation)
* [Testing](#testing)
* [License](#license)

## Overview

Koi-clj is a reference implementation of the [DEP-6](https://github.com/DEX-Company/DEPs/tree/master/6) implemented in Clojure. 
It provides 

- [REST API Endpoints](https://github.com/DEX-Company/DEPs/tree/master/6#methods) compliant with the methods described in DEP-6.
- Sample operations
  - Hashing: an Operation that returns a hash of an Asset
  - Asset hashing: an Operation that accepts an input asset, hashes it, and returns the hash content as a new asset.
  - Finding Prime Numbers: an Operation that returns a list of prime numbers below a given number.
  - Iris Predictor: an example of a machine learning classifier that predicts a single class each instance of the Iris dataset.
  
Example of execution of an operation:
  
#### Cleaning empty rows

This operation accepts an Asset (of type CSV), and returns a new Asset that removes empty rows.

  - accepts an asset as input
  - returns an asset, the content of which is the cleaned dataset.
  
Example:

The JSON sample below is the payload sent to the `/invoke` endpoint of the Service provider.

- params (input) field
```json
{"dataset":{"did":"asset_did_of_dataset_to_filter"}}
```

The response sent by the endpoint on successful completion of the operation
 
```json
{"filtered-dataset":{"did":"asset_did_of_filtered_dataset"}}
```

  
# Running locally

### Prerequisites:

- Install jdk1.8+
- Install [lein](https://leiningen.org)
 
Steps:

``` bash 
git clone https://github.com/DEX-Company/koi-clj/
cd koi-clj
lein run
```

Navigate to <hostname>:8191

# Configuration

Running Koi-clj requires an Agent configuration to be specified. An example config can be found at [config.edn](https://github.com/DEX-Company/koi-clj/blob/develop/resources/config.edn). 

# Documentation 

The [doc](https://github.com/DEX-Company/koi-clj/tree/develop/doc) includes documentation on

- Implementing a new operation
- Creating metadata for a new operation

## API Documentation

- Here's the [link for API docs](https://dex-company.github.io/koi-clj/)

# Testing

- `lein test` runs the unit tests

# License

```
Copyright 2018-2019 DEX Pte. Ltd.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
