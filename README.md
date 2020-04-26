# SaveCopy

## Background

One of the biggest changes of Android 11 is that all apps targeting 30 can only access its' private folder. Google Play will force new/updated apps to upgrade their target API one year later, so apps must make changes.

However, the problem is, some app does not do this correctly. For example, some chat apps, save **files users received from other users** to their **private folder** (`Android/data`) and does not provide options to copy/move these files. In Android 11, no one except the app itself can access those files, so users have to open those apps every time. This is very inconvenient and unreasonable. At least those apps allow the user to open files with other apps. So we have a chance.

This app does a very simple thing, handle `ACTION_VIEW`, and save the file to the `Download` folder.