<p align="center"><a href="https://www.graph.cool"><img src="https://imgur.com/he8RLRs.png"></a></p>

[Website](https://www.graph.cool/) • [Docs](https://graph.cool/docs/) • [Blog](https://blog.graph.cool/) • [Forum](https://www.graph.cool/forum) • [Slack](https://slack.graph.cool/) • [Twitter](https://twitter.com/graphcool)

[![CircleCI](https://circleci.com/gh/prisma/graphcool-framework.svg?style=shield)](https://circleci.com/gh/graphcool/graphcool-framework) [![Slack Status](https://slack.prisma.io/badge.svg)](https://slack.graph.cool) [![npm version](https://badge.fury.io/js/graphcool.svg)](https://badge.fury.io/js/graphcool)

**Graphcool is an open-source backend development framework** to develop and deploy GraphQL APIs.

> To learn more about building scalable and production-ready GraphQL servers, be sure to check out [Prisma](https://www.prisma.io).

## Contents

* [Features](#features)
* [Architecture](#architecture)
* [Deployment](#deployment)
* [FAQ](#faq)
* [Community](#community)
* [Contributing](#contributing)

## Features

#### Graphcool enables rapid development

* Extensible & incrementally adoptable
* No vendor lock-in through open standards
* Rapid development using powerful abstractions and building blocks

#### Includes everything needed for a GraphQL backend

* GraphQL Database with automatic migrations
* JWT-based authentication & flexible permission system
* Realtime GraphQL Subscription API
* GraphQL specification compliant
* Compatible with existing libraries and tools (such as GraphQL.js & Apollo)

#### Scalable serverless architecture designed for the cloud

* Docker-based cluster runtime deployable to AWS, Google Cloud, Azure or any other cloud
* Enables asynchronous, event-driven workflows using serverless functions
* Http based database connections optimised for serverless functions

#### Integrated developer experience from zero to production

* Rapid local development workflow – also works offline
* Supports multiple languages including Node.js and Typescript
* [GraphQL Playground](https://github.com/graphcool/graphql-playground): Interactive GraphQL IDE
* Supports complex continuous integration/deployment workflows

## Architecture

Graphcool is a new kind of framework that introduces clear boundaries between your business logic and stateful components. This separation allows the framework to take advantage of modern cloud infrastructure to scale the stateful components without restricting your choice of programming language and development workflow.

![](https://imgur.com/zaaFVnF.png)

## GraphQL Database

The most important component in the Graphcool Framework is the GraphQL Database:

* Query, mutate & stream data via GraphQL CRUD API
* Define and evolve your data model using GraphQL SDL

If you have used the Graphcool Backend as a Service before, you are already familiar with the benefits of the GraphQL Database.

The CRUD API comes out of the box with advanced features such as pagination, expressive filters and nested mutations. These features are implemented within an efficient data-loader engine, to ensure the best possible performance.

## Deployment

Graphcool services can be deployed with [Docker](https://docker.com/) or the [Graphcool Cloud](http://graph.cool/cloud).

### Docker

You can deploy a Graphcool service to a local environment using Docker. To run a graphcool service locally, use the `graphcool local` sub commands.

This is what a typical workflow looks like:

```sh
graphcool init     # bootstrap new Graphcool service
graphcool local up # start local cluster
graphcool deploy   # deploy to local cluster
```

### Graphcool Cloud (Backend-as-a-Service)

Services can also be deployed to _shared_ clusters in the Graphcool Cloud. When deploying to a shared cluster, there is a **free developer plan** as well as a convenient and efficient **pay-as-you-go pricing** model for production applications.

The Graphcool Cloud currently supports three [regions](https://blog.graph.cool/new-regions-and-improved-performance-7bbc0a35c880):

* `eu-west-1` (EU, Ireland)
* `ap-northeast-1` (Asia Pacific, Tokyo)
* `us-west-1` (US, Oregon)

## FAQ

### What's the relation between Graphcool and Prisma?

[Prisma](https://www.prisma.io) 

### Wait a minute – isn't Graphcool a Backend-as-a-Service?

While Graphcool started out as a Backend-as-a-Service (like Firebase or Parse), [we're currently in the process](https://blog.graph.cool/graphcool-framework-preview-ff42081b1333) of turning Graphcool into a backend development framework. You can still deploy your Graphcool services to the [Graphcool Cloud](https://graph.cool/cloud), and additionally you can run Graphcool locally or deploy to your own infrastructure.

### Why is Graphcool Core written in Scala?

At the core of the Graphcool Framework is the GraphQL Database, an extremely complex piece of software. We developed the initial prototype with Node but soon realized that it wasn't the right choice for the complexity Graphcool needed to deal with.

We found that to be able to develop safely while iterating quickly, we needed a powerful type system. Scala's support for functional programming techniques coupled with the strong performance of the JVM made it the obvious choice for Graphcool.

Another important consideration is that the most mature GraphQL implementation - [Sangria](https://github.com/sangria-graphql) - is written in Scala.

### Is the API Gateway layer needed?

The API gateway is an _optional_ layer for your API, adding it to your service is not required. It is however an extremely powerful tool suited for many real-world use cases, for example:

* Tailor your GraphQL schema and expose custom operations (based on the underlying CRUD API)
* Intercept HTTP requests before they reach the CRUD API; adjust the HTTP response before it's returned
* Implement persisted queries
* Integrate existing systems into your service's GraphQL API
* File management

Also realize that when you're not using an API gateway, _your service endpoint allows everyone to view all the operations of your CRUD API_. The entire data model can be deduced from the exposed CRUD operations.

## Community

Graphcool has a community of thousands of amazing developers and contributors. Welcome, please join us! 👋

* [Forum](https://www.graph.cool/forum)
* [Slack](https://slack.graph.cool/)
* [Stackoverflow](https://stackoverflow.com/questions/tagged/graphcool)
* [Twitter](https://twitter.com/graphcool)
* [Facebook](https://www.facebook.com/GraphcoolHQ)
* [Meetup](https://www.meetup.com/graphql-berlin)
* [Email](hello@graph.cool)

## Contributing

Your feedback is **very helpful**, please share your opinion and thoughts!

### +1 an issue

If an existing feature request or bug report is very important for you, please go ahead and :+1: it or leave a comment. We're always open to reprioritize our roadmap to make sure you're having the best possible DX.

### Requesting a new feature

We love your ideas for new features. If you're missing a certain feature, please feel free to [request a new feature here](https://github.com/graphcool/framework/issues/new). (Please make sure to check first if somebody else already requested it.)
