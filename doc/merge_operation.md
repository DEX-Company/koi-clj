# SGCarmart 

This particular use case demonstrates the use of the Dex stack among participants who would want to share and monetize data, but may not trust all the participants in the data ecosytem.

## Use case:

John is a consumer who is in the market for a used car. His prefers to visit an online marketplace to shortlist cars, after which he plans to test drive the shortlisted cars. 

In order to make an informed decision, John needs information from a set of sources:

- Information provided by the current owner of the car
  - E.g. current mileage, condition
- Information provided by car workshop(s)
  - E.g. car was serviced on date XX, and had a mileage of YY
- Information provided by transportation authorities
  - E.g. this car was registered by person X on date Y 
- Information provided by mandatory car inspection(s)
  - E.g. condition of car on date X was so-and-so
  

Additionally, all(or part) of this information may be made available on a used car portal such as SGCarmart.

Imagine a startup (or SGCarmart)  decides that they want to build such a marketplace. In the best case scenario, once a seller puts a car on their portal, they would request 
- All the car workshops in Singapore
- The transportation authorities 
- All the mandatory car inspection workshops

To part with information about the car. All of the above data owners would agree to sell the data for a price, and the consumer would pay for a report, be satisfied that the car isn’t a lemon, and go ahead to purchase it.

In a less than ideal world, the participants in this data exchange might ask more questions.

- The prospective car buyer might want to check the integrity of the workshop report (e.g. workshop X said that they repaired the brakes last year, can I get access to their raw report, or verify that SGCarmart got data from the raw report?). 
- The car workshops might want to audit the number of data assets resold (e.g. SGCarmart informs me that 20 reports have been sold to end users, but how do I know its 20 and not 20,000).

In order to solve these challenges, we must recognize that the participants in this data exchange viz: SGCarmart, the workshops, the inspection workshops and the transportation authorities
- Need a system that provides a verifiable record of data asset transfers.
- Need a method to transform and merge data assets available on the system.

## Trustworthiness

SGCarmart needs to examine the trust between the parties in the data exchange


This diagram (from the Trust Framework Chapter 2) can be used by SGCarmart to determine 
- What kind of asset is on offer
- The marketplace requirements 

Given that the data contains personal information, is not open, it must be made available as a Data asset (and assigned a trustworthiness class).

On checking the flow diagram for trustworthiness classes, given that we know
- This is not an open data asset
- It contains personally identifiable information (e.g. VIN number, number plate)
- It is not highly confidential (Is this a valid assumption?)

Therefore, the vehicle report should be assigned a trustworthiness class of 2. The trust framework makes a recommendation for each security objective.


| Security                                  | Objective Level |
|-------------------------------------------|-----------------|
| UAI (Utility, Authenticity, Integrity)    | Medium          |
| PAR (Possession, Availability, Resilient) | Medium          |
| Confidentiality                           | Medium          |

For a detailed note on the what these levels indicate, please read the Trust framework, Chapter 3, page 46 onwards. 

## Actors and Responsibilities

We already know that the Dex/Ocean protocol stack enables these features. The rest of this discussion is written from the point of view of a technical implementer who would like to use the stack to deliver similar use cases.



Lets first discuss the actors , their needs and responsibilities in this data exchange

### Actors:

- Prospective car buyer (e.g. John)
- Report creator/owner (e.g. SGCarmart)
- Car workshop(s) (e.g. Performance motors)
- Car inspection workshop(s) (e.g. Vicom)
- Transport authority (e.g. LTA)
- Question: should the prospective car buyer have an identity on the system? 

### Common responsibilities

- All actors must have an account 
- They must acquire currency and be able to transact on data assets in the system.

SGCarmart plays the role of the intermediary in this two sided marketplace. 
On one side are the data providers 3,4,5, and on the other side is the data buyer John.

SGCarmart has 2 categories of deliverables. 

- It must purchase data from actor(s) in roles 3,4,5, merge the data, and generate a report that can be consumed by John. 
- It must provide evidence to 
  - John about the provenance of data in the report (e.g. who are the data providers , when was the report generated). 
  - the data providers about the number of transactions done on the purchased data assets.

### Data merging

Let’s assume that the common 'key' in all the datasets is the VIN number (Vehicle information number).

The information provided by 3,4,5 will be json data, in which one of the fields will be the VIN number.

Example: 

#### Performance motors
```json
{
   "reportid": { "VIN": "abcdef",
                 "parts replaced": { "brake":"braketype "}
               } 
}
```

#### Vicom 
```json
{
   "reportid": { "VIN": "abcdef",
                 "brake status": { "front brake":"good"},
               //other details elided
               } 
}
```

#### LTA 
```json
{
   "reportid": { "VIN": "abcdef",
                 “Owners”: [“Owner  name Kumar”, “Owner John Doe”],
               //other details elided
               }
}
```
               
## Technical implementation



### Prerequisites:

LTA, VICOM and Performance motors have registered the assets ahead of time. Here’s a quick peek into the process of registering assets using Python. 
- The assets that are published are identified with a DID. 

SGCarmart needs to do the following:

- Identify the data assets (by the DID)
- Develop an operation to merge the data assets. It must then register this operation, and run the operation for each consumer requesting a report
- Provide or document  way for John and the data providers to browse the provenance. (Is this possible right now ?)

The rest of this document describes how to develop and run the operation. It also assumes that the reader is familiar with programming.

Let’s assume you have to write a function that accepts 
- 3 maps as input. The data provided by Vicom, LTA and Performance Motors is a map which is keyed by the VIN number.
- The VIN Number (which identifies the vehicle)
- The map returned by the function is also a map, which merges all the maps against the particular VIN number key.

Example: 

```clj 
(select-vin vin-number lta-map vicom-map performance-motors-map)
```

Writing an invokable operation is similar to writing such a function, with a few notable differences:
- The operation is run remotely, and is called via a REST API. However, the Starfish library simplifies the task of interacting with a remote operation.
- You don’t get the result directly, the Starfish library  will register the result as an asset, which can then be consumed. 

