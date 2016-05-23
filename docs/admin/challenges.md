## Challenge API

***
### Create or update a Challenge

`POST /api/v2/challenge`

Creates a challenge

`PUT /api/v2/challenge/:id`

Updates a challenge. Only the required properties that you wish to update and the id are required in each task JSON.

#### Payload

Here is an example challenge:

    {
        "name": "Example Challenge",
        "parent": 1,
        "identifier": "Custom_identifier",
        "difficulty": 1,
        "description": "Description for challenge",
        "blurb": "Blurb for challenge",
        "instruction": "Instruction for challenge"
    }

#### Response
 
POST `201 Created`
PUT `200 OK`

    {
        "id":1,
        "name": "Example Challenge",
        "identifier": "Custom_identifier",
        "difficulty": 1,
        "description": "Description for challenge",
        "blurb": "Blurb for challenge",
        "instruction": "Instruction for challenge",
        "help": "Help on how to fix tasks"
    }
    
#### Properties

* **name** - The name of the challenge _required_
* **parent** - The id of the parent project, if not set, will fall into the default project bucket _optional_
* **identifier** - A custom identifier that can be used to reference the challenge _unique_ per project _optional_
* **difficulty** - Difficulty level of the challenge (1 - EASY, 2 - NORMAL, 3 - EXPERT). If not provided or invalid will default to 1. _optional_
* **description** - challenge description provided in plain text _optional_
* **blurb** - a short blurb about this challenge provided in plain text _optional_
* **instruction** - a longer challenge instruction, can be HTML _optional_

***
### Batch Upload

`POST /api/v2/challenges`

Will create a batch of challenges supplied in a json array.

`PUT /api/v2/challenges`

Will update a batch of challenges supplied in a json array. Only the required properties that you wish to update and the id are required in each task JSON.

#### Example Payload

    [
        {
            "name": "Example Challenge",
            "parent": 1,
            "identifier": "Custom_identifier",
            "difficulty": 1,
            "description": "Description for challenge",
            "blurb": "Blurb for challenge",
            "instruction": "Instruction for challenge"
        },
        ...
    ]
    
The only limitation on this batch upload is the file size of the payload, which is limited to 2048K.

***
### Building a hierarchy

The POST and PUT methods can be used to create object hierarchies as well, so by modifying the payload slightly, you can create challenges and tasks all in one call.

#### Example Payload:

    {
        "name": "Root Challenge",
        "identifier": "Custom_Identifier",
        "difficulty": 1,
        "description": "Challenge description",
        "blurb": "Challenge blurb",
        "instruction": "Challenge instruction"
        "children": [
            {
                 "name": "Task 1",
                 "identifier": "Custom_Task_Identifier",
                 "instruction": "Task instruction",
                 "location": {"type":"Point","coordinates":[77.6255107,40.5872232]},
                 "status": 0
            },
            ...
        ]
    }
    
The only limit with this payload is in regards to the file size of the payload, which is limited to 2048K. Each creation / update of the children objects is handled by the creation / update of the specific object. So anything that would apply in the child execution is applied in this execution. Objects can be created or updated in one single process, so if you include the "id" field in any of the objects it will attempt to update the provided object. Each object's parent will be updated as is defined in the hierarchy, so if you include it in the object json, it will be ignored.

Please review [Task API Doc](tasks.md) for more information around the specifics of creating / updating challenges and tasks.

***
### Delete a challenge

`DELETE /api/v2/challenge/:id`

This will delete a challenge and all the tasks associated with the challenge

#### Response

`204 NoContent`

***
### Get a challenge

`GET /api/v2/challenge/:id`

This will retrieve a specific challenge with all it's information

#### Example Response

`200 OK`
   
    {
        "id": 1,
        "name": "Example Challenge",
        "parent": 1,
        "identifier": "Custom_identifier",
        "difficulty": 1,
        "description": "Description for challenge",
        "blurb": "Blurb for challenge",
        "instruction": "Instruction for challenge"
    }

***
### Listing Challenges

`GET /api/v2/challenges`

This will retrieve a list of the challenges

#### Query String options

* **limit** - Limit the number of returned challenges, default value of 10 if not provided. _optional_
* **page** - The page, this allows you to retrieve X number of challenges at a time and then increment the page to get the next set. Starting page (and default if not provided) is 0. _optional_

#### Example response

`200 OK`

    [
        {
            "id": 1,
            "name": "Example Challenge",
            "parent": 1,
            "identifier": "Custom_identifier",
            "difficulty": 1,
            "description": "Description for challenge",
            "blurb": "Blurb for challenge",
            "instruction": "Instruction for challenge"
        },
        ...
    ]

***
### Listing Challenge Children (Tasks)

`GET /api/v2/challenge/:id/tasks`

This will retrieve a list of tasks that are children of the challenge with the id :id

#### Query String options

* **limit** - Limit the number of returned tasks, default value of 10 if not provided. _optional_
* **page** - The page, this allows you to retrieve X number of tasks at a time and then increment the page to get the next set. Starting page (and default if not provided) is 0. _optional_

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
        }
        ...
    ]

*** 
### Creating / Updating Challenge Children (Tasks)

`POST /api/v2/challenge/:id/tasks`

Will create multiple tasks from the supplied post body json array.

`PUT /api/v2/challenge/:id/tasks`

Will update multiple tasks from the supplied put body json array. Only the required properties that you wish to update and the id are required in each task JSON.

#### Example Payload

    [
        {
            "name": "ExampleTask",
            "identifier": "Custom_Identifier",
            "parent": 1,
            "instruction": "Task instruction",
            "location": {"type":"Point","coordinates":[77.6255107,40.5872232]},
            "status": 0
        }
    ]
    
#### Response

POST `201 Created`
PUT `204 NoContent`

***
### List challenge tasks within Json structure

`GET /api/v2/challenge/:id/children`

This will retrieve a list of tasks that are contained within a project json data structure. This is only slightly different from the api call `GET /api/v2/challenge/:id/tasks` in that it responds with the challenge json at the head.

#### Query String options

* **limit** - Limit the number of returned tasks, default value of 10 if not provided. _optional_
* **page** - The page, this allows you to retrieve X number of tasks at a time and then increment the page to get the next set. Starting page (and default if not provided) is 0. _optional_

#### Example Response

    {
        "id":1,
        "name": "Root Challenge",
        "identifier": "Custom_Identifier",
        "difficulty": 1,
        "description": "Challenge description",
        "blurb": "Challenge blurb",
        "instruction": "Challenge instruction"
        "children": [
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
    }

***
### Get Random task in Challenge

`GET /api/v2/challenge/:id/tasks`

Will retrieve a list of random tasks from the within the challenge. 

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
