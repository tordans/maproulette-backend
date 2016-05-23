## Task API

### Create or Update Task

`POST /api/v2/task`

Creates a Task.

`PUT /api/v2/task/:id`

Updates a Task. Only the required properties that you wish to update and the id are required in each task JSON.

#### Payload

Here is an example task:

    {
        "name": "ExampleTask",
        "identifier": "Custom_Identifier",
        "parent": 1,
        "instruction": "Task instruction",
        "geometries": {
            "type": "FeatureCollection",
            "features":
                [{
                    "type": "Feature",
                    "geometry": {
                        "type": "Point",
                        "coordinates": [77.6255107,40.5872232]
                    },
                    "properties": {}
                }]
        },
        "status": 0
    }

#### Response

POST `201 Created`
PUT `200 OK`

    {
        "id": 1,
        "name": "ExampleTask",
        "identifier": "Custom_Identifier",
        "parent": 1,
        "instruction": "Task instruction",
        "location": {"type":"Point","coordinates":[77.6255107,40.5872232]},
        "status": 0
    }

#### Properties

* **name** - The name of the task _required_
* **parent** - The id of the parent challenge _required_
* **identifier** - A custom identifier that can be used to reference the challenge _unique_ per challenge _required_
* **instruction** - The instruction for how to fix the task
* **location** - A location for the task
* **status** - Current status for the task (0 - Created, 1 - Fixed, 2 - False Positive, 3 - Skipped, 4 - Deleted)

***
### Tagging Tasks

Any task that is created or updated can have tags applied to them during the creation of update of the task. This includes during hierarchical builds and during batch uploads. Doing this is as simple as including the "fulltags" JSON array or the "tags" comma separated string list. If you include a tag name that is already in the database, it will use that tag and not create a new one with a new id.

#### JSON Object Example

    {
        "fulltags": [
            {
                "name":"tag1",
                "description":"tag1 description"
            },
            ...
        ]
    }

#### Comma separated string list example

    {
        "tags": "tag1,tag2,tag3"
    }

The above two examples should be injected into the Task JSON object.

***
### Deleting Tags from a Task

`DELETE /api/v2/task/:id/tags`

Will delete the connection between a task and one or more tags contained in the "tags" query string variable. It will not delete the tag or the task, just the connection between the two.

#### Query String options

* **tags** - A comma separated list of tags that we wish to filter the tasks by. _required_

#### Response

`204 NoContent`

***
### Batch Upload

`POST /api/v2/tasks`

Will create a batch of tasks supplied in a json array.

`PUT /api/v2/tasks`

Will update a batch of tasks supplied in a json array. Only the required properties that you wish to update and the id are required in each task JSON.

#### Example Payload

    [
        {
            "name": "ExampleTask",
            "identifier": "Custom_Identifier",
            "parent": 1,
            "instruction": "Task instruction",
            "location": {"type":"Point","coordinates":[77.6255107,40.5872232]},
            "status": 0
        },
        ...
    ]

The only limitation on this batch upload is the file size of the payload, which is limited to 2048K.

***
### Delete a Task

`DELETE /api/v2/task/:id`

This will delete a task

#### Response

`204 NoContent`

***
### Get a Task

`GET /api/v2/task/:id`

This will retrieve a specific task with all it's information

#### Example Response

`200 OK`

    {
        "id": 1,
        "name": "ExampleTask",
        "identifier": "Custom_Identifier",
        "parent": 1,
        "instruction": "Task instruction",
        "location": {"type":"Point","coordinates":[77.6255107,40.5872232]},
        "status": 0
    }

***
### Gets tags for a task

`GET /api/v2/task/:id/tags`

Returns a list of tag objects that are associated with the task.

### Example Response

`200 OK`

    [
        {
            "id":1,
            "name":"tag1",
            "description":"Description of tag"
        },
        ...
    ]

For more information about tagging see [Tagging API](tags.md)

***
### Listing Tasks based on tags

`GET /api/v2/tasks/tags`

This will retrieve a list of the tasks based on supplied tags

#### Query String options

* **tags** - A comma separated list of tags that we wish to filter the tasks by. _required_
* **limit** - Limit the number of returned tasks, default value of 10 if not provided. _optional_
* **page** - The page, this allows you to retrieve X number of tasks at a time and then increment the page to get the next set. Starting page (and default if not provided) is 0. _optional_

#### Example response

`200 OK`

    [
        {
            "id": 1,
            "name": "ExampleTask",
            "identifier": "Custom_Identifier",
            "parent": 1,
            "instruction": "Task instruction",
            "location": {"type":"Point","coordinates":[77.6255107,40.5872232]},
            "status": 0
        },
        ...
    ]

***
### Retrieve random task

`GET /api/v2/tasks/random`

Will retrieve a random tasks based on any task found in the system.

#### Query String options

* **tags** - Limit the tasks based on the tags associated with the tasks. _optional_
* **limit** - Limit the number of returned projects, default value of 1 if not provided. _optional_

#### Example Response

`200 OK`

    [
        {
            "id": 1,
            "name": "ExampleTask",
            "identifier": "Custom_Identifier",
            "parent": 1,
            "instruction": "Task instruction",
            "location": {"type":"Point","coordinates":[77.6255107,40.5872232]},
            "status": 0
        },
        ...
    ]

***
