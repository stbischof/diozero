package com.diozero;

import java.io.Closeable;
import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.diozero.api.*;
import com.diozero.util.SleepUtil;

/**
 * User's manual:
 * https://docs.google.com/document/d/1Y-yZnNhMYy7rwhAgyL_pfa39RsB-x2qR4vP8saG73rE/edit#
 * Product specification:
 * http://www.micropik.com/PDF/HCSR04.pdf
 * 
 * Provides 2cm - 400cm non-contact measurement function, the ranging accuracy
 * can reach to 3mm You only need to supply a short 10uS pulse to the trigger
 * input to start the ranging, and then the module will send out an 8 cycle
 * burst of ultrasound at 40 kHz and raise its echo. The Echo is a distance
 * object that is pulse width and the range in proportion. We suggest to use over
 * 60ms measurement cycle, in order to prevent trigger signal to the echo signal
 * (Pi4j):				sudo java -classpath dio-zero.jar:pi4j-core.jar com.diozero.HCSR04 17 27
 * (JDK Device I/O):	sudo java -classpath dio-zero.jar -Djava.security.policy=config/gpio.policy com.diozero.HCSR04 17 27
 *
 */
public class HCSR04 implements DistanceSensorInterface, Closeable {
	private static final Logger logger = LogManager.getLogger(HCSR04.class);
	
	public static void main(String[] args) {
		if (args.length != 2) {
			logger.error("Usage: HCSR04 <trigger GPIO> <echo GPIO>");
			System.exit(1);
		}
		int trigger_gpio = Integer.parseInt(args[0]);
		int echo_gpio = Integer.parseInt(args[1]);
		try (HCSR04 device = new HCSR04()) {
			device.init(trigger_gpio, echo_gpio);
			
			while (true) {
				logger.info("Distance = " + device.getDistanceCm() + " cm");
				SleepUtil.sleepMillis(1000);
			}
		} catch (IOException ex) {
			logger.error("I/O error with HC-SR04 device: " + ex.getMessage(), ex);
		}
	}

	private static long MS_IN_SEC = 1000;
	private static long US_IN_SEC = MS_IN_SEC * 1000;
	private static long NS_IN_SEC = US_IN_SEC * 1000;
	// Spec says #10us pulse (min) = 10,000 ns
	private static final int PULSE_NS = 10_000; 
	private static final double MAX_DISTANCE_CM = 400; // Max distance measurement
	private static final double SPEED_OF_SOUND_CM_PER_S = 34029; // Approx Speed of Sound at sea level and 15 degC
	// Calculate the max time (in ns) that the echo pulse stays high
	private static final long MAX_ECHO_TIME_NS = (int) (MAX_DISTANCE_CM * 2 * NS_IN_SEC / SPEED_OF_SOUND_CM_PER_S);

	private DigitalOutputDevice trigger;
	private DigitalInputDevice echo;

	/**
	 * Initialise GPIO to echo and trigger pins
	 *
	 * @param triggerGpioPin
	 * @param echoGpioPin
	 */
	public void init(int triggerGpioNum, int echoGpioNum) throws IOException {
		// Define device for trigger pin at HCSR04
		trigger = new DigitalOutputDevice(triggerGpioNum);
		// Define device for echo pin at HCSR04
		echo = new DigitalInputDevice(echoGpioNum, GpioPullUpDown.NONE, GpioEventTrigger.BOTH);

		trigger.setValue(false);
		// Wait for 0.5 seconds
		SleepUtil.sleepSeconds(0.5);
	}

	/**
	 * Send a pulse to HCSR04 and compute the echo to obtain distance
	 *
	 * @return distance in cm
	 */
	@Override
	public double getDistanceCm() throws IOException {
		long start = System.nanoTime();
		// Send a pulse trigger of 10 us duration
		trigger.setValue(true);
		SleepUtil.sleep(0, PULSE_NS);// wait 10 us (10,000ns)
		trigger.setValue(false);
		
		// Need to include as little code as possible here to avoid missing pin states
		while (!echo.getValue()) {
			if (System.nanoTime() - start > 500_000_000) {
				logger.error("Timeout exceeded waiting for echo to go high");
				return -1;
			}
		}
		long echo_on_time = System.nanoTime();
		
		while (echo.getValue()) {
			if (System.nanoTime() - echo_on_time > 500_000_000) {
				logger.error("Timeout exceeded waiting for echo to go low");
				return -1;
			}
		}
		long echo_off_time = System.nanoTime();
		
		logger.info("Time from echo on to echo off = " + (echo_off_time - echo_on_time) + ", max allowable time=" + MAX_ECHO_TIME_NS);

		double ping_duration_s = (echo_off_time - echo_on_time) / (double)NS_IN_SEC;

		// Distance = velocity * time taken
		// Half the ping duration as it is the time to the object and back
		double distance = SPEED_OF_SOUND_CM_PER_S * (ping_duration_s / 2.0);
		if (distance > MAX_DISTANCE_CM) {
			distance = MAX_DISTANCE_CM;
		}

		return distance;
	}

	/**
	 * Free device GPIOs
	 */
	@Override
	public void close() {
		logger.debug("close()");
		if (trigger != null) { trigger.close(); }
		if (echo != null) { echo.close(); }
	}
}
