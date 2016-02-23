## Tagging API

All tasks in the system can have multiple tags applied to it. The tags allow certain properties or features of a task to be collected and fixed instead of being limited to just the parent challenge of the task. In this way certain sets of tasks could have the tag "CHN" which would imply that the task being worked on is in china. Then someone could conceivably work on multiple types of projects/challenges within China and not worry about the specific hierarchy surrounding the tasks themselves. For this example there would be nothing restricting a user from adding the "CHN" tag to a task that is not within China's borders.

### Create or Update Tag

`POST /api/v2/tag`

Creates a Tag.

`PUT /api/v2/tag/:id`

Updates a Tag. Only the required properties that you wish to update and the id are required in each tag JSON.

#### Payload

Here is an example tag:

    {
        "name": "ExampleTag",
        "description": "Example description for tag"
    }
    
#### Response
 
POST `201 Created`
PUT `200 OK`

    {
        "id": 1,
        "name": "ExampleTag",
        "description": "Example description for tag"
    }
    
#### Properties

* **name** - The name of the tag _required_
* **description** - The description of the tag _optional_

***
### Batch Upload

`POST /api/v2/tags`

Will create a batch of tags supplied in a json array.

`PUT /api/v2/tags`

Will update a batch of tags supplied in a json array. Only the required properties that you wish to update and the id are required in each tag JSON.

#### Example Payload

    [
        {
             "name": "ExampleTag",
            "description": "Example description for tag"
        },
        ...
    ]
    
The only limitation on this batch upload is the file size of the payload, which is limited to 2048K.

***
### Delete a Tag

`DELETE /api/v2/tag/:id`

This will delete a tag

#### Response

`204 NoContent`

***
### Get a Tag

`GET /api/v2/tag/:id`

This will retrieve a specific tag with all it's information

#### Example Response

`200 OK`
   
    {
        "id": 1,
        "name": "ExampleTag",
        "description": "Example description for tag"
    }

*** 
### Retrieve a list of tags

`GET /api/v2/tags`

Retrieves a list of tags

* **prefix** - A prefix for the tags you wish to retrieve. Example, if you wish to retrieve all tags that start with the letter A.
* **limit** - Limit the number of returned tasks, default value of 10 if not provided. _optional_
* **page** - The page, this allows you to retrieve X number of tasks at a time and then increment the page to get the next set. Starting page (and default if not provided) is 0. _optional_

***
