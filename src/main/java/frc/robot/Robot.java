// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot;

import edu.wpi.first.net.PortForwarder;
import edu.wpi.first.wpilibj.TimedRobot;
import edu.wpi.first.wpilibj2.command.CommandScheduler;

public class Robot extends TimedRobot {
    public static Robot INSTANCE;

    private RobotContainer robotContainer;

    public Robot() {
        super();
        INSTANCE = this;
    }

    @Override
    public void robotInit() {
        // this.initializeAdvantageKit();

        this.robotContainer = new RobotContainer();
        this.robotContainer.onRobotInit();

        for(int port = 5800; port <= 5809; port++) {
            PortForwarder.add(port, "limelight.local", port);
        }
    }

    @Override
    public void robotPeriodic() {
        CommandScheduler.getInstance().run();
        this.robotContainer.onRobotPeriodic();
    }

    @Override
    public void disabledInit() {
    }

    @Override
    public void disabledPeriodic() {
    }

    @Override
    public void autonomousInit() {
        this.robotContainer.onAutonomousInit();
        this.robotContainer.getAutonomousCommand().schedule();
    }

    @Override
    public void autonomousPeriodic() {
    }

    @Override
    public void teleopInit() {
        this.robotContainer.onTeleopInit();
    }

    @Override
    public void teleopPeriodic() {
        this.robotContainer.onTeleopPeriodic();
    }

    @Override
    public void testInit() {
        CommandScheduler.getInstance().cancelAll();
    }

    /**
     * This function is called periodically during test mode.
     */
    @Override
    public void testPeriodic() {
    }

    /**
     * This function is called once when the robot is first started up.
     */
    @Override
    public void simulationInit() {
    }

    /**
     * This function is called periodically whilst in simulation.
     */
    @Override
    public void simulationPeriodic() {
    }
}
