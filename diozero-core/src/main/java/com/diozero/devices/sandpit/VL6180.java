package com.diozero.devices.sandpit;

/*-
 * #%L
 * Organisation: diozero
 * Project:      diozero - Core
 * Filename:     VL6180.java
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

import com.diozero.api.I2CDevice;
import com.diozero.api.RuntimeIOException;
import com.diozero.devices.DistanceSensorInterface;

/**
 * Datasheet: https://www.st.com/resource/en/datasheet/vl6180.pdf
 *
 * SparkFun implementation:
 * https://github.com/sparkfun/SparkFun_ToF_Range_Finder-VL6180_Arduino_Library/blob/master/src/SparkFun_VL6180X.cpp
 *
 * Pololu: https://github.com/pololu/vl6180x-arduino/blob/master/VL6180X.cpp
 *
 * Python: https://github.com/adafruit/Adafruit_CircuitPython_VL6180X
 */
public class VL6180 implements DistanceSensorInterface {
	private I2CDevice device;

	@Override
	public void close() {
		device.close();
	}

	@Override
	public float getDistanceCm() throws RuntimeIOException {
		// TODO Auto-generated method stub
		return 0;
	}
}
