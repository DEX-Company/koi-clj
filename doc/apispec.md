# API spec for Invoke


Anyone can run an a service (known as an Agent in Ocean parlance) and participate in the Ocean protocol.

Lets assume that the agent has a `hashing` service they'd like to offer to Ocean consumers. For the purposes of this discussion, lets assume that the service takes 2 types of inputs

- an Ocean asset id, or
- a URL

and returns the hash of the content, which in the first case, is the Ocean asset, or the file available at the URL. The Agent has implemented the service and would like to make it available in the Ocean ecosystem.In order to do the same, the agent needs to follow these steps in different phases.

## TLDR version

### Development phase 

- Implement a REST API that adheres to the Ocean Invoke API Contract(TBD).
- On invocation of a POST request to `/invoke`, call the implementation of the hashing service.

### Deployment phase 

- Host the REST API at a internet-accessible URL. Let's assume it is `https://hashingprovider.com:8080`'(description TBD )
- Create a DDO which defines the metadata for the hosted service.
- Initialize the Starfish library, and replace brizo_url with the value of the host+port where the REST API is available.
- Register the service as an asset along with the metadata created in step 2.
- The service is now available.

## Consumer view

- The consumer initializes the Starfish library, and provides `https://hashingprovider.com:8080` as the value of brizo_url.
- The consumer finds the service and purchases it with their account.
- Consumer creates the payload and verifies that it is valid according to the agent's schema for that service.
- Consumer calls the service by invoking ocean.invoke(payload) and get a synchronous or asynchronous response. 
