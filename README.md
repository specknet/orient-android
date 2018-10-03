# orient-android

PDIoT Week 3 Tutorial
---------------------

The aim this week is to develop the Android app that will receive Bluetooth LE data form your step tracker, perform some analysis and display the results to the user. So far you've been collecting and analysing data offline but we'll now want to process the data in realtime to provide a live step count.

Initially you can continue to use the Orient sensor and switch over to your own mbed implementation during the second half of the practical.

BLE Introduction
----------------

Bluetooth Low Energy (BLE) provides a cheap and reliable way for low power devices to communicate. Devices advertise one of more services, which themselves contain a number of characteristics. For example a heart rate monitor may provide a service which contains a characteristic which will send the current pulse rate. Characteristics can either be readable, writable or allow notifications, which means that new data will be streamed over BLE when it is available. This is the mode that we use to send accelerometer and gyroscope data from the Orient device.

The Nordic Semiconductor NRF Connect app (available on play/app store) will allow you to connect to BLE devices and interrogate the services and characteristics that they provide. It can also send/receive data and log communications to a file, which can be useful for debugging. Try this with the Orient device to see how the sensor data is send over BLE. Gyroscope, accelerometer and magnetometer data are packed into an 18 byte packet, where each axis of each sensor requires 2 bytes to send a 16 bit value.

BLE on Android
--------------

This repository contains the PDIoT data collection app, which you can use as an example of BLE communication on Android. We use the RXAndroidBLE library https://polidea.github.io/RxAndroidBle which simplifies much of the communication code. Note that this requires Java 8 support (enabled in build.gradle as shown below).

android {
	compileOptions {
    	    sourceCompatibility JavaVersion.VERSION_1_8
        	targetCompatibility JavaVersion.VERSION_1_8
    }
}

Goal for Week 4
---------------

- Demonstrate your steptracking algorithm running in an Android app displaying a live step count on level ground.


You will need to extend this app to provide step tracking and add a suitable user interface.
