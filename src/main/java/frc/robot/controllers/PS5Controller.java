package frc.robot.controllers;

import edu.wpi.first.wpilibj2.command.button.CommandPS5Controller;
import edu.wpi.first.wpilibj2.command.button.Trigger;

/**
 * A wrapper around {@link CommandPS5Controller}.
 */
public class PS5Controller extends AbstractController {
    public static final double IGNORE_DELTA = 0.08;

    private final CommandPS5Controller controller;

    public PS5Controller(CommandPS5Controller controller) {
        this.controller = controller;
    }

    @Override
    public double getRightHorizontalMovement() {
        return AbstractController.deadzone(controller.getRightX(), IGNORE_DELTA);
    }

    @Override
    public double getRightVerticalMovement() {
        return -AbstractController.deadzone(controller.getRightY(), IGNORE_DELTA);
    }

    @Override
    public double getLeftHorizontalMovement() {
        return AbstractController.deadzone(controller.getLeftX(), IGNORE_DELTA);
    }

    @Override
    public double getLeftVerticalMovement() {
        return -AbstractController.deadzone(controller.getLeftY(), IGNORE_DELTA);
    }

    @Override
    public boolean getRawButtonWrapper(int button) {
        return controller.getHID().getRawButton(button);
    }

    @Override
    public boolean getRawButtonReleasedWrapper(int button) {
        return controller.getHID().getRawButtonReleased(button);
    }

    @Override
    public boolean getRawButtonPressedWrapper(int button) {
        return controller.getHID().getRawButtonPressed(button);
    }

    @Override
    public Trigger button(int button) {
        return this.controller.button(button);
    }

    @Override
    public Trigger upperButton() {
        return this.controller.triangle();
    }

    @Override
    public Trigger leftButton() {
        return this.controller.square();
    }

    @Override
    public Trigger rightButton() {
        return this.controller.circle();
    }

    @Override
    public Trigger lowerButton() {
        return this.controller.cross();
    }

    @Override
    public int getPOV() {
        return this.controller.getHID().getPOV();
    }

    @Override
    public Trigger leftShoulderButton() {
        return this.controller.L1();
    }

    @Override
    public Trigger rightShoulderButton() {
        return this.controller.R1();
    }

    @Override
    public Trigger leftShoulderTrigger() {
        return this.controller.L2();
    }

    @Override
    public Trigger rightShoulderTrigger() {
        return this.controller.R2();
    }

    @Override
    public Trigger pov(int value) {
        return this.controller.pov(value);
    }

    @Override
    public Trigger leftJoystickButtonTrigger() {
        return this.controller.L3();
    }

    @Override
    public Trigger rightJoystickButtonTrigger() {
        return this.controller.R3();
    }
}
