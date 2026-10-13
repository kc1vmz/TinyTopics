# TinyTopics

Tiny Topics is a topic-based content generator built for tiny content.

Tiny Topics allows you do define a topic using a key (40 characters max) and determine how that content is generated.

You can generate content statically and define the values, or use one of several content generators.

Each generator declares what variables it can take as input, allowing the content to be even more dynamic!

Once a topic is created, content from that topic can be requested via HTTP along with necessary parameters.

Text responses can be limited by the caller to suit their needs. All content is limited to 4096 characters, but can be made smaller to ensure it works for technologies like APRS that need less.


## Installation

Whether on Linux or Microsoft Windows, each platform has an installation script that will download the necessary files and configure the required environment variables and directories.  These are included in every release, or available in the installers/ directory tree in the source code.

The installation asks for the following information:

Where should Tiny Topics be installed?:

Where should Tiny Topics database space be located?:

Where should Tiny Topics temp space be located?:

What port should the HTTP service listen on?:

What port should the admin HTTP service listen on?:

Do you want Tiny Topics to be configured as services and started at boot time (Y/n)?:  (Linux only)



Tiny Topics uses a small file-based database to retain information.

Tiny Topics uses two HTTP ports - a read-only API port, and an admin port with the user interface and read-write API.  This allows you to only make the read-only API port available to the public without exposing the UI and read-write APIs.


### Environment variables

Tiny Topics relies on the following environment variables:

TT_INSTALL_DIR - installation directory

TT_DB_DIR - database location directory

TT_TEMP_DIR - temporary files directory

TT_READ_HTTP_PORT - read-only API port 

TT_ADMIN_HTTP_PORT - read-write API and UI port



###  Pre-requisites

Tiny Topics is a Java application and requires Java 21 to be pre-installed prior to installation.


## Managing topics

Topics can be created, modified and deleted based on your content needs.


### Creating a new topic

From the UI, press the Create Topic button in the top right corner of the window.

Provide a topic ID that will be used when querying the content over HTTP.

Provide a description of the topic for consumers to see what it provides.

Optional parameter names are the list of parameters that can be passed into the topic when requesting content.

There are two types of topics - one that has static content, and one that is generated content.

For static content, select "Text" for the Type, and provide the content below.  Each possible value, randomly selected at each request, should be entered in the Values field, one line per value.

For dynamic content, select "Generated" and then choose the content generator.  The parameters needed will update above.  If you wish to provide a default value for any parameter above, enter it below in the "Saved Settings" edit field in the format of parameter=value.

Save the topic by pressing the Save button when complete.


#### Available content generators

Tiny Topics has a few built-in content generators.  Their content may come from the internet or not.

National Weather Service Alerts - USA-based NWS alerts based on location  (internet-based)

National Weather Service Forecast - USA-based NWS forecast based on location (internet-based)

Sunrise / sunset - learn today's sunrise and sunset based on location (internet-based)

Fortune cookie - provide a glimpse into your future! (internet-based)

Magic 8 Ball - get a classic response from the Magic 8 ball!


### Other topic operations

Topics can be edited and deleted as necessary. Click on the appropriate buttons in the UI to act.



## Topic responses

Topic responses can be obtained for each topic using HTTP.

http://servername:READ_ONLY_PORT/tinyTopicResult/topic-id?length=X&param1=value1&...

Sending the request to the read-only API port, replace "topic-id" with the topic id specified when creating a topic. Parameter "length" is required to limit the length of the returned text.  Topics will only return text at or below that length.  Each parameter can then be added to the request in a parameter=value format.

Example:

http://localhost:8088/api/v1/tinyTopicResult/sunrise-sunset?length=65&x=-71.57&y=42.222

This returns the sunrise and sunset times for the current day based on a specific longitude and latitude.


Topic responses are returned as JSON and contain information about the request and the specific content.

Example:

{
  "topicId": "sunrise-sunset",
  "requestedAt": "2026-10-08T02:40:58.791027400Z",
  "parameters": {
    "x": "-71.57",
    "y": "42.222"
  },
  "status": "CONTENT",
  "text": "Sunrise 06:49; sunset 18:17",
  "errorMessage": null
}

Most importantly from this response is the "text" and "status" fields.  "status" can be  CONTENT, NO_CONTENT, or ERROR.

Detailed information about the APIs and data can be found at http://SERVERNAME:ADMIN_PORT/swagger-ui/index.html
