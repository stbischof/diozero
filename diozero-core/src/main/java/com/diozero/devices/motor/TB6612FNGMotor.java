package com.diozero.devices.motor;

/*-
 * #%L
 * Organisation: diozero
 * Project:      diozero - Core
 * Filename:     TB6612FNGMotor.java
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

import com.diozero.api.DigitalOutputDevice;
import com.diozero.api.PwmOutputDevice;
import com.diozero.api.RuntimeIOException;

/**
 * Bi-directional motor controlled by a single PWM pin and separate forward /
 * backward GPIO pins.
 *
 * Toshiba TB6612FNG Dual Motor Driver such as
 * <a href="https://www.pololu.com/product/713">this one from Pololu</a>.
 *
 * <dl>
 * <dt>Turn forward</dt>
 * <dd>set pin 1 to HIGH, pin 2 to LOW, and PWM to &gt;0.</dd>
 * <dt>Turn backward</dt>
 * <dd>set pin 1 to LOW, pin 2 to HIGH, PWM to &gt;0.</dd>
 * </dl>
 */
public class TB6612FNGMotor extends MotorBase {
	private DigitalOutputDevice motorForwardControlPin;
	private DigitalOutputDevice motorBackwardControlPin;
	private PwmOutputDevice motorPwmControl;

	public TB6612FNGMotor(DigitalOutputDevice motorForwardControlPin, DigitalOutputDevice motorBackwardControlPin,
			PwmOutputDevice motorPwmControl) {
		this.motorForwardControlPin = motorForwardControlPin;
		this.motorBackwardControlPin = motorBackwardControlPin;
		this.motorPwmControl = motorPwmControl;
	}

	@Override
	public void close() {
		Logger.trace("close()");
		stop();
		if (motorForwardControlPin != null) {
			try {
				motorForwardControlPin.close();
			} catch (Exception e) {
				// Ignore
			}
		}
		if (motorBackwardControlPin != null) {
			try {
				motorBackwardControlPin.close();
			} catch (Exception e) {
				// Ignore
			}
		}
		if (motorPwmControl != null) {
			try {
				motorPwmControl.close();
			} catch (Exception e) {
				// Ignore
			}
		}
	}

	@Override
	public void forward(float speed) throws RuntimeIOException {
		motorBackwardControlPin.off();
		motorForwardControlPin.on();
		motorPwmControl.setValue(speed);
		valueChanged(speed);
	}

	@Override
	public void backward(float speed) throws RuntimeIOException {
		motorForwardControlPin.off();
		motorBackwardControlPin.on();
		motorPwmControl.setValue(speed);
		valueChanged(-speed);
	}

	@Override
	public void stop() throws RuntimeIOException {
		motorForwardControlPin.off();
		motorBackwardControlPin.off();
		motorPwmControl.setValue(0);
		valueChanged(0);
	}

	@Override
	public float getValue() throws RuntimeIOException {
		float speed = motorPwmControl.getValue();

		return motorForwardControlPin.isOn() ? speed : -speed;
	}

	@Override
	public boolean isActive() throws RuntimeIOException {
		return motorPwmControl.isOn() && (motorForwardControlPin.isOn() || motorBackwardControlPin.isOn());
	}
}
