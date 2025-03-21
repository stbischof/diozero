package com.diozero.sampleapps;

/*-
 * #%L
 * Organisation: diozero
 * Project:      diozero - Sample applications
 * Filename:     GarminLidarV4Test.java
 * 
 * This file is part of the diozero project. More information about this project
 * can be found at https://www.diozero.com/.
 * %%
 * Copyright (C) 2016 - 2021 diozero
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * #L%
 */

import org.tinylog.Logger;

import com.diozero.devices.GarminLidarLiteV4;
import com.diozero.util.Hex;

/*-
 * Unit Id: 560C0003
 * Hardware Version: 16. A
 * Firmware Version: 210. 2.10
 * Lib Version: BKY2.02B00 
 * Board Temperature: 37
 * SoC Temperature: 36
 * Power mode: ALWAYS_ON
 * Max acquisition count: 255
 * Detection sensitivity: 0
 * High accuracy mode: 20
 * High accuracy mode enabled: true
 * Flash storage enabled: false
 * Quick acquisition termination enabled: false
 */
public class GarminLidarV4Test {
	public static void main(String[] args) {
		try (GarminLidarLiteV4 lidar = new GarminLidarLiteV4()) {
			System.out.println("Unit Id: " + Hex.encodeHexString(lidar.getUnitId()));
			int hardware_version = lidar.getHardwareVersion();
			System.out.println("Hardware Version: " + hardware_version + ". "
					+ GarminLidarLiteV4.HardwareRevision.valueOf(hardware_version));
			int firmware_version = lidar.getFirmwareVersion();
			System.out.print("Firmware Version: " + firmware_version + ". v");
			System.out.println((firmware_version / 100) + "." + (firmware_version % 100));
			System.out.println("Lib Version: " + lidar.getLibraryVersion());

			System.out.println("Board Temperature: " + lidar.getBoardTemperature() + " dec C");
			System.out.println("SoC Temperature: " + lidar.getSoCTemperature() + " dec C");

			System.out.println("Power mode: " + lidar.getPowerMode());
			System.out.println("Max acquisition count: " + lidar.getMaximumAcquisitionCount());
			System.out.println("Detection sensitivity: " + lidar.getDetectionSensitivity());
			System.out.println("High accuracy mode: " + lidar.getHighAccuracyMode());
			System.out.println("High accuracy mode enabled: " + lidar.isHighAccuracyModeEnabled());
			System.out.println("Flash storage enabled: " + lidar.isFlashStorageEnabled());
			System.out.println("Quick acquisition termination enabled: " + lidar.isQuickAcquistionTerminationEnabled());

			while (true) {
				int reading = lidar.getSingleReading();
				System.out.println("Distance: " + reading + " cm");
				Thread.sleep(100);
			}
		} catch (InterruptedException e) {
			Logger.info(e, "Interrupted: {}", e);
		}
	}
}
