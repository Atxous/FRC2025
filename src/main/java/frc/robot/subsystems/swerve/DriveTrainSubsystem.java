// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.subsystems.swerve;

import com.pathplanner.lib.auto.AutoBuilder;
import com.pathplanner.lib.config.PIDConstants;
import com.pathplanner.lib.config.RobotConfig;
import com.pathplanner.lib.controllers.PPHolonomicDriveController;
import edu.wpi.first.math.MathUtil;
import edu.wpi.first.math.Matrix;
import edu.wpi.first.math.Nat;
import edu.wpi.first.math.VecBuilder;
import edu.wpi.first.math.estimator.SwerveDrivePoseEstimator;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.math.kinematics.SwerveDriveKinematics;
import edu.wpi.first.math.kinematics.SwerveModulePosition;
import edu.wpi.first.math.kinematics.SwerveModuleState;
import edu.wpi.first.networktables.DoubleArrayPublisher;
import edu.wpi.first.networktables.DoublePublisher;
import edu.wpi.first.networktables.StructArrayPublisher;
import edu.wpi.first.networktables.StructPublisher;
import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.smartdashboard.Field2d;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.Commands;
import edu.wpi.first.wpilibj2.command.InstantCommand;
import edu.wpi.first.wpilibj2.command.RunCommand;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.robot.Constants.PortConstants;
import frc.robot.Constants.RobotConstants;
import frc.robot.Flags;
import frc.robot.Robot;
import frc.robot.commands.drive.pathfinding.PathfindingManager;
import frc.robot.commands.drive.pathfinding.pathfinders.LocalADStar2;
import frc.robot.subsystems.staticsubsystems.LimeLight;
import frc.robot.subsystems.staticsubsystems.LimeLight.LimeyApriltagReading;
import frc.robot.subsystems.staticsubsystems.QuestNav;
import frc.robot.subsystems.staticsubsystems.RoboGUI;
import frc.robot.subsystems.staticsubsystems.RobotGyro;
import frc.robot.util.NetworkTablesUtil;
import frc.robot.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static edu.wpi.first.units.Units.RadiansPerSecond;

/**
 * Represents a swerve drive style drivetrain.
 */
public class DriveTrainSubsystem extends SubsystemBase {
	public static final DoubleArrayPublisher pathfinderPathPub = NetworkTablesUtil.MAIN_ROBOT_TABLE.getDoubleArrayTopic("pathfinder").publish();
	public static final DoubleArrayPublisher connectionPathPub = NetworkTablesUtil.MAIN_ROBOT_TABLE.getDoubleArrayTopic("connection_path").publish();
	public static final double MAX_SPEED_METERS_PER_SEC = Flags.DriveTrain.LOWER_MAX_SPEED ? 1.5 : 3;
	public static final double MAX_ROT_SPEED_ANGULAR = 3;
	private static final DoubleArrayPublisher rawPosePub = NetworkTablesUtil.MAIN_ROBOT_TABLE.getDoubleArrayTopic("raw_pose").publish();
	private static final DoubleArrayPublisher chosenPathPub = NetworkTablesUtil.MAIN_ROBOT_TABLE.getDoubleArrayTopic("chosen_path").publish();
	private static final boolean INVERT_DRIVE_MOTORS = true;
	// Location of each swerve drive, relative to motor center. +X -> moving to front of robot, +Y -> moving to left of robot. IMPORTANT.
	private static final Translation2d frontLeftLocation = new Translation2d(RobotConstants.LEG_LENGTHS_M, RobotConstants.LEG_LENGTHS_M);
	private static final Translation2d frontRightLocation = new Translation2d(RobotConstants.LEG_LENGTHS_M, -RobotConstants.LEG_LENGTHS_M);
	private static final Translation2d backLeftLocation = new Translation2d(-RobotConstants.LEG_LENGTHS_M, RobotConstants.LEG_LENGTHS_M);
	private static final Translation2d backRightLocation = new Translation2d(-RobotConstants.LEG_LENGTHS_M, -RobotConstants.LEG_LENGTHS_M);
	
	static final SwerveModuleState[] optimizedTargetStates = new SwerveModuleState[4]; // for debugging purposes
	// public static final double MAX_SPEED = 3.0; // 3 meters per second
	// public static final double MAX_ANGULAR_SPEED = Math.PI; // 1/2 rotation per second
	final double LOCK_HEADING_THRESHOLD = 0.1; // TODO: test if when rotate without translating
	
	private final SwerveModule frontLeft = new SwerveModule(
		PortConstants.CAN.DTRAIN_FRONT_LEFT_DRIVE_MOTOR_ID,
		PortConstants.CAN.DTRAIN_FRONT_LEFT_ROTATION_MOTOR_ID,
		PortConstants.CAN.DTRAIN_FRONT_LEFT_CANCODER_ID,
		"fL_12",
		INVERT_DRIVE_MOTORS,
		true
	);
	private final SwerveModule frontRight = new SwerveModule(
		PortConstants.CAN.DTRAIN_FRONT_RIGHT_DRIVE_MOTOR_ID,
		PortConstants.CAN.DTRAIN_FRONT_RIGHT_ROTATION_MOTOR_ID,
		PortConstants.CAN.DTRAIN_FRONT_RIGHT_CANCODER_ID,
		"fR_03",
		INVERT_DRIVE_MOTORS,
		true
	);
	private final SwerveModule backLeft = new SwerveModule(
		PortConstants.CAN.DTRAIN_BACK_LEFT_DRIVE_MOTOR_ID,
		PortConstants.CAN.DTRAIN_BACK_LEFT_ROTATION_MOTOR_ID,
		PortConstants.CAN.DTRAIN_BACK_LEFT_CANCODER_ID,
		"bL_06",
		INVERT_DRIVE_MOTORS,
		true
	);
	private final SwerveModule backRight = new SwerveModule(
		PortConstants.CAN.DTRAIN_BACK_RIGHT_DRIVE_MOTOR_ID,
		PortConstants.CAN.DTRAIN_BACK_RIGHT_ROTATION_MOTOR_ID,
		PortConstants.CAN.DTRAIN_BACK_RIGHT_CANCODER_ID,
		"bR_01",
		INVERT_DRIVE_MOTORS,
		true
	);
	public final SwerveModule[] swerveModules = {frontLeft, frontRight, backLeft, backRight};
	
	private final SwerveDriveKinematics kinematics = new SwerveDriveKinematics(frontLeftLocation, frontRightLocation, backLeftLocation, backRightLocation);
	private final SwerveDrivePoseEstimator poseEstimator = new SwerveDrivePoseEstimator(kinematics, RobotGyro.getRotation2d(), this.getAbsoluteModulePositions(), new Pose2d(), new Matrix<>(Nat.N3(), Nat.N1(), new double[] {0.1, 0.1, 0.1}), new Matrix<>(Nat.N3(), Nat.N1(), new double[] {0.01, 0.01, 0.1}));
	private final Field2d field = new Field2d();
	private final Field2d pfField = new Field2d();
	private final Field2d estimatedField = new Field2d();
	private final Field2d questNavField = new Field2d();
	private final Field2d limelightField = new Field2d();
	
	// uploads the intended, estimated, and actual states of the robot.
	private final StructArrayPublisher<SwerveModuleState> targetSwerveStatePublisher = NetworkTablesUtil.MAIN_ROBOT_TABLE.getStructArrayTopic("TargetStates", SwerveModuleState.struct).publish();
	private final StructArrayPublisher<SwerveModuleState> realSwerveStatePublisher = NetworkTablesUtil.MAIN_ROBOT_TABLE.getStructArrayTopic("ActualStates", SwerveModuleState.struct).publish();
	private final StructArrayPublisher<SwerveModuleState> absoluteAbsoluteSwerveStatePublisher = NetworkTablesUtil.MAIN_ROBOT_TABLE.getStructArrayTopic("AbsoluteAbsolute", SwerveModuleState.struct).publish();
	private final StructArrayPublisher<SwerveModuleState> relativeSwerveStatePublisher = NetworkTablesUtil.MAIN_ROBOT_TABLE.getStructArrayTopic("RelativeStates", SwerveModuleState.struct).publish();
	
	// publish robot position relative to field
	private final StructPublisher<Pose2d> posePositionPublisher = NetworkTablesUtil.MAIN_ROBOT_TABLE.getStructTopic("estimatedOdometryPosition", Pose2d.struct).publish();
	private final StructPublisher<Rotation2d> robotRotationPublisher = NetworkTablesUtil.MAIN_ROBOT_TABLE.getStructTopic("rotation", Rotation2d.struct).publish();
	private final StructPublisher<Pose2d> questNavPublisher = NetworkTablesUtil.MAIN_ROBOT_TABLE.getStructTopic("questnav", Pose2d.struct).publish();
	private final StructPublisher<Pose2d> limeyPosePublisher = NetworkTablesUtil.MAIN_ROBOT_TABLE.getStructTopic("limeyPose", Pose2d.struct).publish();
	private final DoublePublisher fLAmp = NetworkTablesUtil.MAIN_ROBOT_TABLE.getDoubleTopic("fl_amp").publish();
	private final DoublePublisher fRAmp = NetworkTablesUtil.MAIN_ROBOT_TABLE.getDoubleTopic("fr_amp").publish();
	private final DoublePublisher bLAmp = NetworkTablesUtil.MAIN_ROBOT_TABLE.getDoubleTopic("bl_amp").publish();
	private final DoublePublisher bRAmp = NetworkTablesUtil.MAIN_ROBOT_TABLE.getDoubleTopic("br_amp").publish();
	
	private final List<PathfindingManager> reefedPathfindingManagers;
	Command c = new InstantCommand();
	
	private boolean lockedHeadingMode = false;
	private Rotation2d lockedHeading;
	private RobotConfig config;
	private boolean has = false;
	
	// private final AprilTagHandler aprilTagHandler;
	public DriveTrainSubsystem(/*AprilTagHandler aprilTagHandler*/) {
		this.reefedPathfindingManagers = Util.createIfFlagElseNull(() -> { // https://www.desmos.com/calculator/esepl3jdzw target a path length of 0.75m
			List<PathfindingManager> mgrs = new ArrayList<>();
			String[] reefPrefixes = {"In", "Clockwise", "CounterClockwise"}; // this code section is the first time I wish something could be pythonic
			for (int i = 1; i <= 6; i++) {
				String[] prefixed = new String[reefPrefixes.length];
				for (int j = 0; j < reefPrefixes.length; j++) {
					prefixed[j] = reefPrefixes[j] + "ToReef" + i;
				}
				mgrs.add(new PathfindingManager(List.of(prefixed))); // note: will unpack list to make a List<String> so everything is ok.
			}
			
			String[] humanPlayerPrefixes = {"Right", "Left", ""};
			for (int i = 1; i <= 2; i++) {
				String[] prefixed = new String[humanPlayerPrefixes.length];
				for (int j = 0; j < humanPlayerPrefixes.length; j++) {
					prefixed[j] = humanPlayerPrefixes[j] + "IntoHumanPlayer" + i;
				}
				mgrs.add(new PathfindingManager(List.of(prefixed)));
			}
			
			List<String> otherPathNames = List.of();
			mgrs.add(new PathfindingManager(otherPathNames));
			
			return mgrs;
		}, Flags.DriveTrain.ENABLE_DYNAMIC_PATHFINDING);
		// this.aprilTagHandler = aprilTagHandler;
		
		RobotGyro.resetGyroAngle();
		RobotGyro.setGyroAngle(180);
		
		SmartDashboard.putData("Field", field);
		SmartDashboard.putData("pfField", pfField);
		SmartDashboard.putData("quest field", questNavField);
		SmartDashboard.putData("estimated field", estimatedField);
		SmartDashboard.putData("limey field", limelightField);
		
		QuestNav.INSTANCE.resetPose(new Pose2d());
		QuestNav.INSTANCE.resetHeading(Rotation2d.k180deg);
		// QuestNav.INSTANCE.resetHeading();
		
		this.configureAutoBuilder();
		
		System.out.println("Initialized DriveTrainSubsystem");
		PathfindingManager.configurePathfinder(new LocalADStar2());
		
		if (Flags.DriveTrain.ENABLE_DYNAMIC_PATHFINDING && Util.isSim()) {
			System.out.println("pathplanner test");
			setPose(new Pose2d(3.08, 2.35, Rotation2d.fromDegrees(48)));
			c = Commands.defer(() -> reefedPathfindingManagers.get(4).getFullCommand(getPose()), Set.of(this));
			// c.schedule();
			// System.out.println(poses);
		}
		
		// setPose(new Pose2d(5.75, 1.55, Rotation2d.fromDegrees(124)));
	}
	
	public static double flipFactor() {
	    if (Util.onBlueTeam()) {
	        return 1;
	    } else {
	        return -1;
	    }
	}
	
	private void configureAutoBuilder() {
		try {
			this.config = RobotConfig.fromGUISettings();
		} catch (Exception e) {
			System.out.println("Error while loading robotconfig for auto");
			e.printStackTrace();
		}
		
		AutoBuilder.configure(
			this::getPose,
			this::setPose,
			this::getRobotRelativeChassisSpeeds,
			this::consumeChassisSpeeds,
			new PPHolonomicDriveController(
				new PIDConstants(1.255, 0, 0),
				new PIDConstants(1.4, 0, 0)
			),
			config, // womp womp if its null
			() -> !Util.onBlueTeam(),
			this
		);
		
		PathfindingManager.configure(
			this::getPose,
			this::getRobotRelativeChassisSpeeds,
			this::consumeChassisSpeeds,
			new PPHolonomicDriveController(
				new PIDConstants(1.35, 0, 0),
				new PIDConstants(1.5, 0, 0)
			),
			config, // womp womp if its null
			() -> !Util.onBlueTeam(),
			this
		);
	}
	
	/**
	 * Get the absolute positions of all swerve modules, using the CANCoder's *relative* mode for the module heading. Arranged as FL, FR, BL, BR
	 *
	 * @return The position of all swerve modules, with module heading determined by the CANCoder's relative mode.
	 */
	public SwerveModulePosition[] getAbsoluteModulePositions() {
		return new SwerveModulePosition[] {
			frontLeft.getAbsoluteModulePosition(),
			frontRight.getAbsoluteModulePosition(),
			backLeft.getAbsoluteModulePosition(),
			backRight.getAbsoluteModulePosition()
		};
	}
	
	public void consumeChassisSpeeds(ChassisSpeeds chassisSpeeds) {
		this.consumeRawModuleStates(kinematics.toSwerveModuleStates(chassisSpeeds));
	}
	
	/**
	 * The robot's current estimated pose, as estimated by the pose estimator using motor rotations and vision measurements.
	 *
	 * @return The current pose of the robot.
	 */
	public Pose2d getPose() {
		return this.poseEstimator.getEstimatedPosition();
	}
	
	/**
	 * Set the robot's current pose
	 *
	 * @param pose The desired current code
	 */
	public void setPose(Pose2d pose) {
		RobotGyro.setGyroAngle(pose.getRotation().getDegrees());
		// System.out.println(RobotGyro.getRotation2d());
		poseEstimator.resetPosition(pose.getRotation(), this.getAbsoluteModulePositions(), pose);
		
		QuestNav.INSTANCE.resetPose(pose);
		QuestNav.INSTANCE.resetHeading(pose.getRotation());
	}
	
	/**
	 * Get the current chassis speeds of the robot, relative to the robot.
	 *
	 * @return The current chassis speeds of the robot, relative to the robot.
	 */
	public ChassisSpeeds getRobotRelativeChassisSpeeds() {
		return kinematics.toChassisSpeeds(
			frontLeft.getAbsoluteModuleState(),
			frontRight.getAbsoluteModuleState(),
			backLeft.getAbsoluteModuleState(),
			backRight.getAbsoluteModuleState()
		);
	}
	
	/**
	 * Get a {@link Command} to rotate all modules to absolute zero. This command will time itself out if incomplete after 1 second. Regardless of completion, all non-absolutely-absolute encoders (see {@link SwerveModule#resetEncodersToAbsoluteValue()}) are set to the absolutely-absolute encoder's value.
	 *
	 * @return A {@link Command} to rotate all modules to absolute zero.
	 * @see SwerveModule#resetEncodersToAbsoluteValue()
	 */
	public Command rotateToAbsoluteZeroCommand() {
		return new RunCommand(() -> {
			frontLeft.rotateToAbsoluteZero(0);
			frontRight.rotateToAbsoluteZero(1);
			backLeft.rotateToAbsoluteZero(2);
			backRight.rotateToAbsoluteZero(3);
		}, this).until(() -> { // Until all modules have a heading of <= 0.03 rad
			for (SwerveModule module : swerveModules) {
				if (Math.abs(module.getAbsoluteModulePosition().angle.getRadians()) > 0.04) {
					return false;
				}
			}
			return true;
			// TODO: test! This timeout is kind of low and could cause rotateToAbsoluteZero to be unreliable
		}).withTimeout(1.0).andThen(() -> { // or it takes more than one second
			for (SwerveModule module : swerveModules) { // then we want to set all of them to the absolutely-absolute value (not zero, since we might not actually be at zero)
				module.resetEncodersToAbsoluteValue();
			}
		});
	}
	
	public void setHeadingLockMode(boolean lockedHeading) {
		this.lockedHeadingMode = lockedHeading;
	}
	
	/**
	 * Method to drive the robot using joystick info.
	 *
	 * @param forwardSpeed  Speed of the robot in the x direction (forward).
	 * @param sidewaysSpeed Speed of the robot in the y direction (sideways).
	 * @param rotSpeed      Angular rate of the robot.
	 * @param fieldRelative Whether the provided x and y speeds are relative to the field.
	 */
	public void drive(double forwardSpeed, double sidewaysSpeed, double rotSpeed, boolean fieldRelative) {
		if (!Flags.DriveTrain.ENABLED) {
			return;
		}
		
		rotSpeed = applyLockHeadingMode(forwardSpeed, sidewaysSpeed, rotSpeed);
		
		ChassisSpeeds chassisSpeeds;
		if (fieldRelative) {
			chassisSpeeds = ChassisSpeeds.fromFieldRelativeSpeeds(forwardSpeed, sidewaysSpeed, rotSpeed, RobotGyro.getRotation2d());
		} else {
			chassisSpeeds = new ChassisSpeeds(forwardSpeed, sidewaysSpeed, rotSpeed);
		}
		
		if (Flags.DriveTrain.ENABLE_ANGULAR_VELOCITY_COMPENSATION_TELEOP && Robot.INSTANCE.isTeleop()) {
			// TODO: WE SET THIS TO 0.1 AT START. COULD JUST BE MAKING SKEW WORSE GOING IN OPPOSITE DIRECTION
			// TODO: WE NEED TO TUNE THE CON STANT TO MAKE GOOD !
			chassisSpeeds = angularVelocitySkewCorrection(chassisSpeeds, 0.3);
		}
		
		SwerveModuleState[] swerveModuleStates = kinematics.toSwerveModuleStates(chassisSpeeds);
		
		SwerveDriveKinematics.desaturateWheelSpeeds(swerveModuleStates, MAX_SPEED_METERS_PER_SEC);
		frontLeft.setDesiredState(swerveModuleStates[0], 0);
		frontRight.setDesiredState(swerveModuleStates[1], 1);
		backLeft.setDesiredState(swerveModuleStates[2], 2);
		backRight.setDesiredState(swerveModuleStates[3], 3);
		
		// TODO: This is publishing a newly instantiated list UNRELATED to anything
		targetSwerveStatePublisher.set(optimizedTargetStates);
	}
	
	// @SuppressWarnings("unused")
	private double applyLockHeadingMode(double forwardSpeed, double sidewaysSpeed, double rotSpeed) {
		double speed = Math.sqrt(Math.pow(forwardSpeed, 2) + Math.pow(sidewaysSpeed, 2));
		boolean isMoving = speed > LOCK_HEADING_THRESHOLD;
		if (Flags.DriveTrain.ENABLE_LOCKED_HEADING_MODE && isMoving) {
			// As soon as the robot isn't rotating (the driver's thumb isn't on the rotation joystick anymore),
			// we set lockedHeadingMode to true and store the current heading so we can keep moving at it.
			boolean robotIsTurning = Math.abs(rotSpeed) > 0.01;
			if (robotIsTurning) {
				lockedHeadingMode = false;
			} else if (lockedHeadingMode) {
				// locked heading mode is ON! move back towards previous orientation.
				double headingError = lockedHeading.getRadians() - RobotGyro.getRotation2d().getRadians();
				double unboundedRotSpeed = 1.0 * headingError; // 1.0 is changeable constant (like kP)
				if (unboundedRotSpeed >= 0.005) {
					rotSpeed = MathUtil.clamp(unboundedRotSpeed, -0.3, 0.3);
				}
			} else {
				// if we JUST STOPPED turning, we store the current orientation so we can move back towards it later
				lockedHeadingMode = true;
				lockedHeading = RobotGyro.getRotation2d();
			}
		}
		return rotSpeed;
	}
	
	public ChassisSpeeds angularVelocitySkewCorrection(ChassisSpeeds robotRelativeVelocity, double angularVelocityCoefficient) {
		var angularVelocity = new Rotation2d(RobotGyro.getYawAngularVelocity().in(RadiansPerSecond) * angularVelocityCoefficient);
		if (angularVelocity.getRadians() != 0.0) {
			Rotation2d heading = RobotGyro.getRotation2d();
			ChassisSpeeds fieldRelativeVelocity = ChassisSpeeds.fromRobotRelativeSpeeds(robotRelativeVelocity, heading);
			robotRelativeVelocity = ChassisSpeeds.fromFieldRelativeSpeeds(fieldRelativeVelocity, heading.plus(angularVelocity));
		}
		return robotRelativeVelocity;
	}
	
	/**
	 * Set all drive motor speeds to a specified value without any PID control.
	 *
	 * @param speed The desired speed, [-1, 1]
	 */
	public void directDriveSpeed(double speed) { // INCHES: 10 rot ~= 18.25, ~ 9 rot ~= 15.75
		frontLeft.directDrive(speed);
		frontRight.directDrive(speed);
		backLeft.directDrive(speed);
		backRight.directDrive(speed);
	}
	
	/**
	 * Set all turn motor speeds to a specified value without any PID control.
	 *
	 * @param speed The desired speed, [-1, 1]
	 */
	public void directTurnSpeed(double speed) {
		frontLeft.directTurn(speed);
		frontRight.directTurn(speed);
		backLeft.directTurn(speed);
		backRight.directTurn(speed);
	}
	
	/**
	 * Set all drive motor voltages to a specified value without any PID control.
	 *
	 * @param volts The desired voltage output
	 */
	public void directDriveVoltage(double volts) {
		frontLeft.setVoltages(volts, 0);
		frontRight.setVoltages(volts, 0);
		backLeft.setVoltages(volts, 0);
		backRight.setVoltages(volts, 0);
	}
	
	/**
	 * Consumes a set of raw {@link SwerveModuleState}s, setting the modules to target those states.
	 *
	 * @param states An array of 4 desired states, in the order FL, FR, BL, BR.
	 */
	public void consumeRawModuleStates(SwerveModuleState[] states) {
		frontLeft.setDesiredState(states[0]);
		frontRight.setDesiredState(states[1]);
		backLeft.setDesiredState(states[2]);
		backRight.setDesiredState(states[3]);
	}
	
	/**
	 * Stops all motors, driving or turning, by sending a target voltage of 0.
	 */
	public void stop() {
		this.frontLeft.stop();
		this.frontRight.stop();
		this.backLeft.stop();
		this.backRight.stop();
	}
	
	@Override
	public void periodic() {
		Pose2d current = this.getPose();
		//publishes each wheel information to network table for debugging
		realSwerveStatePublisher.set(new SwerveModuleState[] {frontLeft.getAbsoluteModuleState(), frontRight.getAbsoluteModuleState(), backLeft.getAbsoluteModuleState(), backRight.getAbsoluteModuleState()});
		// absoluteAbsoluteSwerveStatePublisher.set(new SwerveModuleState[]{frontLeft.getAbsoluteAbsoluteModuleState(), frontRight.getAbsoluteAbsoluteModuleState(), backLeft.getAbsoluteAbsoluteModuleState(), backRight.getAbsoluteAbsoluteModuleState()});
		relativeSwerveStatePublisher.set(new SwerveModuleState[] {frontLeft.getRelativeModuleState(), frontRight.getRelativeModuleState(), backLeft.getRelativeModuleState(), backRight.getRelativeModuleState()});
		//posts robot position to network table
		posePositionPublisher.set(current);
		robotRotationPublisher.set(RobotGyro.getRotation2d());
		questNavPublisher.set(QuestNav.INSTANCE.getPose());
		
		rawPosePub.set(new double[] {current.getX(), current.getY(), current.getRotation().getDegrees()});
		
		Pose2d limeyPose = LimeLight.getLimeyApriltagReading().pose();
		limeyPosePublisher.set(limeyPose);
		
		// for (SwerveModule module : swerveModules) {
		// System.out.println(module.getName() + " rel vel: " + RobotMathUtil.roundNearestHundredth(module.getRelativeTurnVelocity()) + ", abs vel: " + RobotMathUtil.roundNearestHundredth(module.getTurningAbsEncoderVelocityConverted()));
		// System.out.println(module.getName() + " " + module.getDriveRotations());
		// System.out.println(module.getName() + " " + module.getPosition());
		
		// System.out.println(module.getName() + " " + TroyMathUtil.roundNearestHundredth(module.getTurningEncoderPositionConverted()));
		// }
		
		this.updateOdometry();
		// this.updateOdometryWithJetsonVision();
		field.setRobotPose(getPose());
		questNavField.setRobotPose(QuestNav.INSTANCE.getPose());
		limelightField.setRobotPose(limeyPose);
		// System.out.println(this.getPose());
		
		//fLAmp.set(frontLeft.getDriveAmperage());
		//fRAmp.set(frontRight.getDriveAmperage());
		//bLAmp.set(backLeft.getDriveAmperage());
		//bRAmp.set(backRight.getDriveAmperage());
		
		if (Flags.DriveTrain.ENABLE_DYNAMIC_PATHFINDING && Util.isSim()) {
			// System.out.println("pp init: " + AutoBuilder.isConfigured() + " " + AutoBuilder.isPathfindingConfigured());
			if (!c.isScheduled() && !has && Robot.INSTANCE.isEnabled()) {
				c.schedule();
				System.out.println("SCHEDULING ! \nSCHEDULING!\n SCHEDULIGN!");
				has = true;
			} else {
				// System.out.println("command running");
			}
			var poses = Util.convertPPTrajStateListToDoubleArray(reefedPathfindingManagers.get(4).getBestPath(getPose()).getIdealTrajectory(config).orElseThrow().getStates());
			// field.getRobotObject().setPoses(poses);
			chosenPathPub.set(poses);
            /*
            PathPlannerPath p = PathfindingManager.getNewestPathfindingPath();
            if (p != null) {
                var pathfindPoses = p.getPathPoses();
                pfField.getRobotObject().setPoses(pathfindPoses);
                chosenPathPub.set(Util.convertPoseListToDoubleArray(poses));
                pathfinderPathPub.set(Util.convertPoseListToDoubleArray(pathfindPoses));
            } else {
                // System.out.println("null path");
            }*/
		}
	}
	
	/**
	 * THE RETURNED COMMAND SHOULD BE INSTANTLY RUN. DO NOT DELAY SCHEDULING.
	 *
	 * @return A dynamic pathfinding command to reef 1 (aka index 0 in reef list)
	 */
	public Command getFindToReef0Command() {
		if (Flags.DriveTrain.ENABLE_DYNAMIC_PATHFINDING) {
			return reefedPathfindingManagers.get(0).getFullCommand(getPose());
		}
		return new InstantCommand();
	}
	
	/**
	 * THE RETURNED COMMAND SHOULD BE INSTANTLY RUN. DO NOT DELAY SCHEDULING.
	 *
	 * @return A dynamic pathfinding command to the reef number indicated by robot gui in network tables.
	 */
	public Command getFindToSelectedReefCommand() {
		if (Flags.DriveTrain.ENABLE_DYNAMIC_PATHFINDING) {
			System.out.println("generating new command");
			int selection = RoboGUI.getPressedTargetReef();
			RoboGUI.resetPressedTargetReef(); // reset it so we don't run again by accident
			System.out.println("pathing to reef (if it's -2 then it was -1 before): " + (selection - 1));
			if (selection != -1) {
				return reefedPathfindingManagers.get(selection - 1).getFullCommand(getPose()); // minus 1 bc array indexing
			}
		}
		return new InstantCommand();
	}
	
	/**
	 * Updates the field relative position of the robot using module state readouts.
	 */
	public void updateOdometry() {
		this.poseEstimator.update(RobotGyro.getRotation2d(), new SwerveModulePosition[] {frontLeft.getAbsoluteModulePosition(), frontRight.getAbsoluteModulePosition(), backLeft.getAbsoluteModulePosition(), backRight.getAbsoluteModulePosition()});
		if (Flags.DriveTrain.ENABLE_LIMEY_APRILTAGS_ODOMETRY_FUSING) {
			updateOdometryWithLimeyApriltags();
		}
		if (Flags.DriveTrain.ENABLE_OCULUS_ODOMETRY_FUSING) {
			updateOdometryWithOculus();
		}
	}
	
	public void updateOdometryWithOculus() {
		if (QuestNav.INSTANCE.connected()) {
			Pose2d oculus = QuestNav.INSTANCE.getPose();
			this.poseEstimator.addVisionMeasurement(oculus, QuestNav.INSTANCE.timestamp(), VecBuilder.fill(0.007, 0.007, 0.1));
		} else {
			DriverStation.reportWarning("Oculus not connected!", false);
		}
	}
	
	public void updateOdometryWithLimeyApriltags() {
		if (LimeLight.isLimeyConnected()) {
			boolean shouldResetOculus = false;
			boolean shouldResetFullPose = false;
			LimeyApriltagReading reading = LimeLight.getLimeyApriltagReading();
			double correction = Double.POSITIVE_INFINITY;
			if (reading.exists()) {
				if (reading.distance() < 0.6) {
					correction = 0.001; // 0.05 * Math.pow(reading.distance() / 0.61, 6);// Math.pow(0.3 * reading.distance(), 10 / reading.distance());
					shouldResetOculus = true;
					shouldResetFullPose = true;
				} else if (reading.distance() < 0.75) {
					correction = 0.0025;
					shouldResetOculus = true;
					shouldResetFullPose = true;
				} else if (reading.distance() < 1) {
					correction = 0.005;
					shouldResetOculus = true;
					// shouldResetFullPose = true;
				} else if (reading.distance() < 1.5) {
					correction = 0.006;
					shouldResetOculus = true;
				} else if (reading.distance() < 1.75) {
					correction = 0.006;
					// shouldResetOculus = true;
				} else if (reading.distance() < 2) {
					correction = 0.0065;
					// shouldResetOculus = true;
				}
				this.poseEstimator.addVisionMeasurement(reading.pose(), reading.timestamp(), VecBuilder.fill(correction, correction, correction));
				
				if (shouldResetFullPose) {
					// setPose(reading.pose());
				}
				
				if (shouldResetOculus) {
					// System.out.println("Resetting oculus with limey input");
					QuestNav.INSTANCE.resetPose(reading.pose());
				}
			}
		} else {
			DriverStation.reportWarning("Limey not connected!", false);
		}
	}
}