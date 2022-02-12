# VideoTransfer-RC-1

Project for the first assignment for the RC (Redes de Computadores) subject.

## What it does

This project aimed to create a video streaming algorithm to transfer a video to a client as fast and as reliable as possible.

## How to use

Run the `Main` class from the `proxy` package.

Run the docker image using `docker run -ti -p 9999:8080 smduarte/rc2021-tp1-test1` to use the test that has a stable bandwidth.

To test with a variable bandwidth, run it with `docker run -ti -p 9999:8080 smduarte/rc2021-tp1-test2`.

Once the docker container is running, open a browser and search for `http://localhost:9999/index.html`.

Go to `Video` and choose a video from the list. The program should start playing the video after a couple of seconds.

## Disclaimer

The videos portrayed in this project are subject to copyright and/or trademark and are just meant to be a placeholder for testing.

Under no circumstance we take ownership of these videos.
