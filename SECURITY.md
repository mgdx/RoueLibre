# Reporting a security problem

Roue Libre holds no account, no key and no server of its own, so the damage a
flaw can do here is of a particular kind: it is almost always about **what
leaves the phone**, or about **what a file arriving on it can make the
application do**. Those are the reports this page is for.

## What is in scope

- Anything that makes the application **send out** something it promises never
  to send: a position, a destination, a search, an identifier, a journey — or
  any request at all to a host other than the network's own feed and the data
  releases named in [`docs/offline-data.md`](docs/offline-data.md).
- Anything that lets a **downloaded or imported dataset** do more than be read:
  a path escaping the application's own storage, a file that leads to code being
  run, a digest check that can be walked around.
- Anything that lets **another application on the device** read what Roue Libre
  holds, or drive it into acting on data it did not choose: an exported
  component, an intent, a provider, a link opening more than it should.
- Anything that weakens the transport: a request leaving in cleartext, a
  certificate not checked, a redirect followed off the host it started on.
- A dependency shipped in the APK with a known vulnerability that this
  application actually reaches.

## What is not

- A bike-share operator's own feed, site or application. Report those to the
  operator; we only read what they publish.
- A missing feature, however prudent it would be — that is an issue, not a
  vulnerability.
- Anything that needs the device to be already compromised, unlocked and in the
  attacker's hands, or a build the project did not produce.
- Reports produced by a scanner and sent on without a path through this code to
  go with them.

## How to report

**Use GitHub's private advisory form:
<https://github.com/mgdx/RoueLibre/security/advisories/new>.** It is a private
channel between you and the maintainers, it keeps the report and its discussion
in one place, and it costs you no account beyond the one you already have.

If that form is not available to you, open a public issue saying **only** that
you have a security report and how to be reached — no details — and you will be
given a private way to send the rest.

What helps, in order: the version and the way it was installed, the device and
its Android version, what an attacker has to be able to do first, and the
shortest sequence that shows the problem. A patch is welcome and never expected.

## What happens next

- **Within 5 days**, an acknowledgement that a human has read it.
- **Within 30 days**, an assessment: whether it is a flaw, how serious, and what
  the fix is likely to be.
- A fix is released as soon as it is ready, and the advisory is published with
  it. **90 days** after the report, the problem is disclosed whether or not it
  is fixed — a flaw kept quiet indefinitely protects nobody but the project.
- You are credited by the name you ask for, or not at all if you prefer. There
  is no money: this application has no revenue to pay it from.

## Two things worth knowing before you write

**The application sends no log anywhere.** There is no crash reporter, no
telemetry and no analytics — see [`docs/dependencies.md`](docs/dependencies.md).
So nothing about your report reaches us on its own: whatever we learn, we learn
from what you write. If you attach a log you captured yourself with `adb`, read
it through first — it is yours, and it may say where you have been.

**No fix will ever be shipped by a channel of its own.** Roue Libre updates the
way it was installed and no other way; the application downloads data, never
code, and it never will ([`docs/offline-data.md`](docs/offline-data.md)). Anyone
telling you otherwise is not us.
