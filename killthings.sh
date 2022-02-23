#!/bin/bash
jps -l | grep adi | cut -d" " -f1 | xargs kill -9

