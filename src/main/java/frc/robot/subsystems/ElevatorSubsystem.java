package frc.robot.subsystems;

import com.revrobotics.AbsoluteEncoder;
import com.revrobotics.RelativeEncoder;
import com.revrobotics.spark.ClosedLoopSlot;
import com.revrobotics.spark.SparkBase;
import com.revrobotics.spark.SparkClosedLoopController;
import com.revrobotics.spark.SparkLowLevel.MotorType;
import com.revrobotics.spark.SparkMax;
import com.revrobotics.spark.config.SparkBaseConfig.IdleMode;
import com.revrobotics.spark.config.SparkMaxConfig;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.networktables.GenericPublisher;
import edu.wpi.first.networktables.NetworkTableType;
import edu.wpi.first.wpilibj.DigitalInput;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.robot.Constants;
import frc.robot.Flags;
import frc.robot.util.NetworkTablesUtil;
import frc.robot.util.Util;

public class ElevatorSubsystem extends SubsystemBase {
    public static final GenericPublisher leftHeightAbsRotPub = NetworkTablesUtil.getPublisher("robot", "leftElevAR", NetworkTableType.kDouble);
    public static final GenericPublisher rightHeightAbsRotPub = NetworkTablesUtil.getPublisher("robot", "rightElevAR", NetworkTableType.kDouble);

    private static final double MAX_HEIGHT = 1.1;
    private static final double MIN_HEIGHT = 0.0;
    private static final double MAX_OUTPUT_RIGHT_ELEVATOR_PIDS = 0.6;
    private static final double MAX_OUTPUT_LEFT_ELEVATOR_PIDS = 0.5;
    private final static double ABSOLUTE_DEGREES_PER_RELATIVE_DEGREES = 1426.64 / 8254.24;
    private final static double ROTATIONS_PER_METER_ASCENDED = Rotation2d.fromDegrees(742.5).getRotations() / 0.5;
    private final static double METERS_ASCENDED_PER_ROTATION = 1 / ROTATIONS_PER_METER_ASCENDED;

    private static final GenericPublisher leftHeightAbsPub = NetworkTablesUtil.getPublisher("robot", "leftElevAbsH", NetworkTableType.kDouble);
    private static final GenericPublisher rightHeightAbsPub = NetworkTablesUtil.getPublisher("robot", "rightElevAbsH", NetworkTableType.kDouble);
    private static final GenericPublisher leftHeightRelPub = NetworkTablesUtil.getPublisher("robot", "leftElevRelH", NetworkTableType.kDouble);
    private static final GenericPublisher rightHeightRelPub = NetworkTablesUtil.getPublisher("robot", "rightElevRelH", NetworkTableType.kDouble);
    private final SparkMax rightMotor;
    private final SparkMax leftMotor;
    private final RelativeEncoder rightMotorEncoder;
    private final RelativeEncoder leftMotorEncoder;
    private final AbsoluteEncoder rightAbsoluteEncoder;
    private final AbsoluteEncoder leftAbsoluteEncoder;
    private final DigitalInput leftLimitSwitch = new DigitalInput(Constants.PortConstants.DIO.LEFT_ELEVATOR_LIMIT);
    private final DigitalInput rightLimitSwitch = new DigitalInput(Constants.PortConstants.DIO.RIGHT_ELEVATOR_LIMIT);
    private final SparkClosedLoopController rightPIDController;
    private final SparkClosedLoopController leftPIDController;
    private double currentSetpointMeters;

    public ElevatorSubsystem() {
        rightMotor = new SparkMax(Constants.PortConstants.CAN.RIGHT_ELEVATOR_MOTOR_ID, MotorType.kBrushless);
        leftMotor = new SparkMax(Constants.PortConstants.CAN.LEFT_ELEVATOR_MOTOR_ID, MotorType.kBrushless);
        rightMotorEncoder = rightMotor.getEncoder();
        leftMotorEncoder = leftMotor.getEncoder();
        rightAbsoluteEncoder = rightMotor.getAbsoluteEncoder();
        leftAbsoluteEncoder = leftMotor.getAbsoluteEncoder();

        this.rightPIDController = this.rightMotor.getClosedLoopController();
        this.leftPIDController = this.leftMotor.getClosedLoopController();

        // hard vertical limit 1830 degrees
        SparkMaxConfig rightConfig = new SparkMaxConfig();
        SparkMaxConfig leftConfig = new SparkMaxConfig();

        leftConfig
                .inverted(true)
                .smartCurrentLimit(60)
                .idleMode(IdleMode.kBrake)
                .voltageCompensation(12);

        leftConfig.encoder
                .positionConversionFactor(360d * ABSOLUTE_DEGREES_PER_RELATIVE_DEGREES / 762.183 * METERS_ASCENDED_PER_ROTATION)
                .velocityConversionFactor(360d * ABSOLUTE_DEGREES_PER_RELATIVE_DEGREES / 60d / 762.183 * METERS_ASCENDED_PER_ROTATION);

        leftConfig.closedLoop
                .pidf(2.2, 0, 0, 0.1)
                .outputRange(-MAX_OUTPUT_LEFT_ELEVATOR_PIDS, MAX_OUTPUT_LEFT_ELEVATOR_PIDS);


        rightConfig
                .inverted(false)
                .smartCurrentLimit(60)
                .idleMode(IdleMode.kBrake)
                .voltageCompensation(12);

        rightConfig.encoder
                .positionConversionFactor(360d * ABSOLUTE_DEGREES_PER_RELATIVE_DEGREES / 762.183 * METERS_ASCENDED_PER_ROTATION)
                .velocityConversionFactor(360d * ABSOLUTE_DEGREES_PER_RELATIVE_DEGREES / 60d / 762.183 * METERS_ASCENDED_PER_ROTATION);

        rightConfig.closedLoop
                .pidf(2.2, 0, 0, 0.1)
                .outputRange(-MAX_OUTPUT_LEFT_ELEVATOR_PIDS, MAX_OUTPUT_LEFT_ELEVATOR_PIDS);
        
        Util.configureSparkMotor(leftMotor, leftConfig);
        Util.configureSparkMotor(rightMotor, rightConfig);

        // leftMotor.configure(leftConfig, ResetMode.kResetSafeParameters, PersistMode.kPersistParameters);
        // rightMotor.configure(rightConfig, ResetMode.kResetSafeParameters, PersistMode.kPersistParameters);

        rightMotorEncoder.setPosition(0);
        leftMotorEncoder.setPosition(0);

        this.currentSetpointMeters = 0;

        // this.rightThroughboreEncoder.name = "right";
        // this.leftThroughboreEncoder.name = "left";

        //Robot.INSTANCE.addPeriodic(() -> {
        //    this.updateEncoders();
        //}, 1.0/200, 0);
    }

    // Will this cause problems by stopping PID control too soon at the bottom?
    private void resetEncodersIfLimit() {
        if (getLeftLimitSwitch() || getRightLimitSwitch()) {
            resetEncoders();
        }
    }

    private void updateEncoders() {
        //this.leftThroughboreEncoder.periodic();
        //this.rightThroughboreEncoder.periodic();
    }

    /*
     * Returns true if the current height is within five centimeters of the setpoint.
     */
    public boolean getAtSetpoint()
    {
        return Math.abs(getCurrentHeight() - this.currentSetpointMeters) < 0.05;
    }
    
    private void resetMotorEncodersToAbsoluteEncoders() {
        rightMotorEncoder.setPosition(rightAbsoluteEncoder.getPosition());
        leftMotorEncoder.setPosition(leftAbsoluteEncoder.getPosition());
    }

    private void resetEncoders() {
        rightMotorEncoder.setPosition(0);
        leftMotorEncoder.setPosition(0);
    }

    /**
     * @return The average of the left and right heights, in meters
     */
    public double getCurrentHeight() {
        return (getLeftRelativePosition() + getRightRelativePosition()) / 2;
    }

    @Override
    public void periodic() {
        // System.out.println("L ls: " + this.getLeftLimitSwitch() + ", R: " + this.getRightLimitSwitch());
        // updateEncoders();
        /* <--- do not comment out the slash asterisk, just add a double slash in front
        System.out.println("Right position = " + this.getRightThroughboreEncoderDistance() + "\n" + 
                           "Left position = " + this.getLeftThroughboreEncoderDistance() + "\n" + 
                           "Right position (meters)= " + this.getRightMetersAscended() + "\n" + 
                           "Left position (meters)= " + this.getLeftMetersAscended() + "\n" + 
                           "Right position (relative)= " + this.getRightRelativePosition() + "\n" + 
                           "Left position (relative)= " + this.getLeftRelativePosition() + "\n");
        // <-- leave both of these --> */
        // resetEncodersIfLimit();

        // rightHeightAbsPub.setDouble(this.getRightMetersAscended());
        // leftHeightAbsPub.setDouble(this.getLeftMetersAscended());
        rightHeightRelPub.setDouble(this.getRightRelativePosition());
        leftHeightRelPub.setDouble(this.getLeftRelativePosition());

        //leftHeightAbsRotPub.setDouble(this.leftThroughboreEncoder.getAbsolutePosition().getDegrees());
        //rightHeightAbsRotPub.setDouble(this.leftThroughboreEncoder.getAbsolutePosition().getDegrees());
        if (getLeftLimitSwitch() || getRightLimitSwitch()) {
            // resetMotorEncodersToAbsoluteEncoders();
        }
        // System.out.println("Current set/point: " + this.currentSetpointMeters);
    }

    public void setTargetHeight(double heightMeters) {
        // System.out.println("Setting target height to " + heightMeters);
        this.currentSetpointMeters = heightMeters;
        if (Flags.Elevator.ENABLED) {
            if (heightMeters <= MAX_HEIGHT && heightMeters >= MIN_HEIGHT) {
                this.rightPIDController.setReference(heightMeters, SparkBase.ControlType.kPosition, ClosedLoopSlot.kSlot0, 0);
                this.leftPIDController.setReference(heightMeters, SparkBase.ControlType.kPosition, ClosedLoopSlot.kSlot0, 0);
            }
        }
    }

    public void setRelativeEncodersToAbsolute() {
        rightMotorEncoder.setPosition(getRightMetersAscended());
        leftMotorEncoder.setPosition(getLeftMetersAscended());
    }

    public void setRawSpeeds(double rightSpeed, double leftSpeed) {
        if (Flags.Elevator.ENABLED) {
            this.rightMotor.set(rightSpeed);
            this.leftMotor.set(leftSpeed);

            System.out.println("R, L at: " + rightMotor.get() + ", " + leftMotor.get());
        }
    }

    public Rotation2d getRightThroughboreEncoderDistance() {
        return new Rotation2d();//this.rightThroughboreEncoder.getTotalDistance();
    }

    public Rotation2d getLeftThroughboreEncoderDistance() {
        return new Rotation2d();    // this.leftThroughboreEncoder.getTotalDistance();
    }

    public double getRightMetersAscended() {
        return this.getRightThroughboreEncoderDistance().getRotations() * METERS_ASCENDED_PER_ROTATION;
    }

    public double getLeftMetersAscended() {
        return this.getLeftThroughboreEncoderDistance().getRotations() * METERS_ASCENDED_PER_ROTATION;
    }

    public double getLeftRelativePosition() {
        return this.leftMotorEncoder.getPosition();
    }

    public double getRightRelativePosition() {
        return this.rightMotorEncoder.getPosition();
    }

    public boolean getLeftLimitSwitch() {
        return !this.leftLimitSwitch.get(); // active low
    }

    public boolean getRightLimitSwitch() {
        return !this.rightLimitSwitch.get(); // active low
    }
}
