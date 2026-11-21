# Security policy

## Supported versions

Only the [latest release](https://github.com/mikelj14/30-min-app/releases/latest)
gets fixes. Please update before reporting.

## Reporting a vulnerability

Please don't open a public issue for a security problem.

Report it privately instead: go to the
[Security tab](https://github.com/mikelj14/30-min-app/security), choose
**Report a vulnerability**, and describe:

- what the problem is and what an attacker could do with it
- the steps to reproduce it
- the app version (at the bottom of Settings) and your Android version

This is a one-person project. I aim to reply within 7 days and will keep you
updated until it's fixed. Once a fixed version is released, I'll publish an
advisory and credit you if you'd like.

## Scope

30-Min is an Android app with no server, no account and no internet
permission. Everything it stores stays on the phone. In scope:

- the app itself (for example, another app on the phone reading or changing
  its data, or starting or stopping a block)
- the release APKs and their signing
- this repository's build and release workflows

Out of scope: problems that need a rooted phone or physical access to an
unlocked phone, and the emergency exit (ending a block early is a feature).
