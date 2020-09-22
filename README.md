# oss-guidelines

A repo containing all the basic file templates and general guidelines for any open source project at Slack.

## Usage

It's recommended to place all files except `README.md` and `LICENSE` into the `.github/` directory at
the top level of your repository. The `LICENSE` file should be placed at the top level.

Next, you should search and replace all instances of the following strings:

*  `{project_name}`: This is human readable name for your project, sometimes useful to just use the package name for the distribution (e.g. `@slack/events-api`).
*  `{project_slug}`: This is usually the last two path segments of the URL on GitHub (e.g. `slackapi/node-slack-events-api`).
*  `{platform_name}`: The name of the relevant platform this module is built for (e.g. `node.js`).

Next, customize the templates for you needs. At a minimum, you will need to replace the quoted areas
in `maintainers_guide.md` and `pull_request_template.md`. **It is recommended to be familiar with all
the content before committing it into your project.** This repo serves as a base case but there's
room for you to add or remove pieces until it is right for your project.

All the examples above illustrate an [existing package](https://github.com/slackapi/node-slack-events-api) which you can use for guidance.


**NOTE**: Currently, cla-assistant is set up only on the [SlackAPI](https://github.com/slackapi)
Org. It can be set up for other organizations if desired.
