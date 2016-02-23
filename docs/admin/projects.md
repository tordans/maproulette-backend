## Project API

### Create or Update Project
 
`POST /api/v2/project`
 
Creates a project
 
`PUT /api/v2/project/:id`
 
Updates a project. Only the required properties that you wish to update and the id are required in each task JSON.
 
#### Example Payload
 
Here is an example payload:
 
    {
        "name": "Example Project",
        "description": "Example Project description"
    }
    
For updating you need only supply properties you are updating.

#### Example Response

POST `201 Created`
PUT `200 OK`
    
    {
        "id":1,
        "name": "Example Project",
        "description": "Example Project description"
    }
    
#### Properties

* **name** - The name of the project _required_
* **description** - The description of the project _optional_

***
### Batch Upload

`POST /api/v2/projects`

Will create a batch of projects supplied in a json array.

`PUT /api/v2/projects`

Will update a batch of projects supplied in a json array. Only the required properties that you wish to update and the id are required in each task JSON.

#### Example Payload

    [
        {
            "name": "Project 1",
            "description": "Description for Project 1"
        },
        ...
    ]
    
The only limitation on this batch upload is the file size of the payload, which is limited to 2048K.

***
### Building a hierarchy

The POST and PUT methods can be used to create object hierarchies as well, so by modifying the payload slightly, you can create projects, challenges and tasks all in one call.

#### Example Payload:

    {
        "name": "Root Project",
        "description": "This is the root project of the hierarchy",
        "children": [
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
            },
            ...
        ]
    }
    
The only limit with this payload is in regards to the file size of the payload, which is limited to 2048K. Each creation / update of the children objects is handled by the creation / update of the specific object. So anything that would apply in the child execution is applied in this execution. Objects can be created or updated in one single process, so if you include the "id" field in any of the objects it will attempt to update the provided object. Each object's parent will be updated as is defined in the hierarchy, so if you include it in the object json, it will be ignored. As will be described in the [Challenge API Doc](challenges.md) this process can be built from the challenge level as well, that is you would start with a challenge and build it's children.

Please review [Challenge API Doc](challenges.md) and [Task API Doc](tasks.md) for more information around the specifics of creating / updating challenges and tasks.

***
### Delete a project

`DELETE /api/v2/project/:id`

This will delete a project, all it's associated challenges and all the tasks associated with the deleted challenges

#### Response

`204 NoContent`

***
### Get a project

`GET /api/v2/project/:id`

This will retrieve a specific project with all it's information

#### Example Response

`200 OK`
   
    {
        "id": 1,
        "name": "Example Project",
        "description": "Example project description"
    }

***
### Listing Projects

`GET /api/v2/projects`

This will retrieve a list of the projects

#### Query String options

* **limit** - Limit the number of returned projects, default value of 10 if not provided. _optional_
* **page** - The page, this allows you to retrieve X number of projects at a time and then increment the page to get the next set. Starting page (and default if not provided) is 0. _optional_

#### Example response

`200 OK`

    [
        {
            "id": 1,
            "name": "Project 1",
            "description": "Description for project 1"
        },
        ...
    ]

***
### Listing Project Children (Challenges)

`GET /api/v2/project/:id/challenges`

This will retrieve a list of challenges that are children of the project with the id :id

#### Query String options

* **limit** - Limit the number of returned challenges, default value of 10 if not provided. _optional_
* **page** - The page, this allows you to retrieve X number of challenges at a time and then increment the page to get the next set. Starting page (and default if not provided) is 0. _optional_

#### Example Response

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
        }
        ...
    ]

*** 
### Creating / Updating Project Children (Challenges)

`POST /api/v2/project/:id/challenges`

Will create multiple challenges from the supplied post body json array.

`PUT /api/v2/project/:id/challenges`

Will update multiple challenges from the supplied put body json array. Only the required properties that you wish to update and the id are required in each task JSON.

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
        }
    ]
    
#### Response

POST `201 Created`
PUT `204 NoContent`

***
### List Project children within Json structure

`GET /api/v2/project/:id/children`

This will retrieve a list of challenges that are contained within a project json data structure. This is only slightly different from the api call `GET /api/v2/project/:id/challenges` in that it responds with the project json at the head.

#### Query String options

* **limit** - Limit the number of returned challenges, default value of 10 if not provided. _optional_
* **page** - The page, this allows you to retrieve X number of challenges at a time and then increment the page to get the next set. Starting page (and default if not provided) is 0. _optional_

#### Example Response

    {
        "name": "Root Project",
        "description": "This is the root project of the hierarchy",
        "children": [
            {
                "name": "Root Challenge",
                "identifier": "Custom_Identifier",
                "difficulty": 1,
                "description": "Challenge description",
                "blurb": "Challenge blurb",
                "instruction": "Challenge instruction"
            },
            ...
        ]
    }

***
### Get Random task in Project

`GET /api/v2/project/:id/tasks`

Will retrieve a list of random tasks from the within the project. The list will contain tasks that are children of all the children challenges for the provided project.

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
