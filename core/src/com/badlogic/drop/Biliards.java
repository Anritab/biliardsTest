package com.badlogic.drop;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.MathUtils;


import java.util.ArrayList;
import java.util.List;

public class Biliards extends ApplicationAdapter {
	private SpriteBatch batch;
	private OrthographicCamera camera;
	private Texture ballTexture;
	private Sprite ballSprite;
	private Texture obstacleTexture;
	private List<Sprite> obstacles;
	private Vector2 ballPosition;
	private float ballAngle;

	private ShapeRenderer shapeRenderer;
	private Vector2 lightStartPosition;
	private Vector2 lightEndPosition;
	private boolean isColliding;

	private Vector2 redLightStartPosition;
	private Vector2 redLightEndPosition;
	private boolean isRedLightColliding;
	private Sprite collidedObstacle;

	private Vector2 greenLightStartPosition;
	private Vector2 greenLightEndPosition;
	private boolean isGreenLightColliding;
	private Sprite greenCollidedObstacle;

	@Override
	public void create() {
		batch = new SpriteBatch();
		camera = new OrthographicCamera();
		camera.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		ballTexture = new Texture("ball.png");
		ballSprite = new Sprite(ballTexture);
		ballSprite.setSize(50, 50);
		obstacleTexture = new Texture("obstacle.png");
		obstacles = new ArrayList<>();
		ballPosition = new Vector2(Gdx.graphics.getWidth() / 2f, Gdx.graphics.getHeight() / 2f); //располагаем в центре главный шар
		ballAngle = 0f;

		//траектории шаров
		shapeRenderer = new ShapeRenderer();
		lightStartPosition = new Vector2();
		lightEndPosition = new Vector2();
		isColliding = false;

		redLightStartPosition = new Vector2();
		redLightEndPosition = new Vector2();
		isRedLightColliding = false;
		collidedObstacle = null;

		greenLightStartPosition = new Vector2();
		greenLightEndPosition = new Vector2();
		isGreenLightColliding = false;
		greenCollidedObstacle = null;
	}

	@Override
	public void render() {
		handleInput();
		update();
		draw();
	}

	private void handleInput() {
		if (Gdx.input.isKeyPressed(Input.Keys.A)) {
			ballAngle += 2f; //повернуть налево
		}
		if (Gdx.input.isKeyPressed(Input.Keys.D)) {
			ballAngle -= 2f; //повернуть направо
		}
		if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
			//тут создается шар нажатием на ЛКМ
			Vector3 mousePos = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
			Vector3 worldPos = camera.unproject(mousePos);
			Sprite obstacle = new Sprite(obstacleTexture);
			obstacle.setSize(50, 50);
			obstacle.setPosition(worldPos.x - obstacle.getWidth() / 2f, worldPos.y - obstacle.getHeight() / 2f); //чего-то он ругается если Vector2 использовать
			obstacles.add(obstacle);
		}
	}

	private void update() {
		ballSprite.setOriginCenter();
		ballSprite.setRotation(ballAngle);
		ballSprite.setPosition(ballPosition.x - ballSprite.getWidth() / 2f, ballPosition.y - ballSprite.getHeight() / 2f);

		float angleRad = (float) Math.toRadians(ballAngle);
		float length = 500;
		lightStartPosition.set(ballPosition);
		lightEndPosition.set(ballPosition.x + length * (float) Math.cos(angleRad),
				ballPosition.y + length * (float) Math.sin(angleRad));

		checkCollision();

		//устанавливаем красный луч при соприкосновении
		if (isColliding) {
			Vector2 obstacleCenter = new Vector2(collidedObstacle.getX() + collidedObstacle.getWidth() / 2f,
					collidedObstacle.getY() + collidedObstacle.getHeight() / 2f);
			float obstacleAngle = ballAngle - 2 * (ballAngle - calculateAngle(obstacleCenter, ballPosition)); //разворачиваем его

			redLightStartPosition.set(obstacleCenter);
			redLightEndPosition.set(obstacleCenter.x + length * (float) Math.cos(Math.toRadians(obstacleAngle)),
					obstacleCenter.y + length * (float) Math.sin(Math.toRadians(obstacleAngle)));

			checkRedLightCollision();
		} else {
			isRedLightColliding = false;    //если коллизии нет, то сбрасываем ее
			isGreenLightColliding = false;
		}

		//к сожалению, правильное решение для второго пункта не было придумано...
		if (isRedLightColliding && collidedObstacle != null) {
			Vector2 redObstacleCenter = new Vector2(redLightStartPosition);
			Vector2 obstacleCenter = new Vector2(collidedObstacle.getX() + collidedObstacle.getWidth() / 2f,
					collidedObstacle.getY() + collidedObstacle.getHeight() / 2f);
			float redAngle = ballAngle - 2 * (ballAngle - calculateAngle(redObstacleCenter, ballPosition));
			float obstacleAngle = calculateAngle(obstacleCenter, redLightEndPosition);


			//этот угол необходимо отразить относительно относительно прямой, на которой красный и зеленый лучи совпадают
			float greenAngle = 2 * obstacleAngle - redAngle;

			greenLightStartPosition.set(obstacleCenter);
			greenLightEndPosition.set(obstacleCenter.x + length * (float) Math.cos(Math.toRadians(greenAngle)),
					obstacleCenter.y + length * (float) Math.sin(Math.toRadians(greenAngle)));

			checkGreenLightCollision();
		}

	}

	//методы проверки коллизии
	private void checkCollision() {
		isColliding = false;
		collidedObstacle = null;
		Rectangle ballBounds = ballSprite.getBoundingRectangle();
		for (Sprite obstacle : obstacles) {
			Rectangle obstacleBounds = obstacle.getBoundingRectangle();
			// увеличиваем размер зоны проверки коллизии на ширину спрайта шара
			obstacleBounds.setWidth(obstacleBounds.getWidth() + ballBounds.getWidth());
			obstacleBounds.setHeight(obstacleBounds.getHeight() + ballBounds.getHeight());
			obstacleBounds.setPosition(obstacle.getX() - ballBounds.getWidth() / 2f, obstacle.getY() - ballBounds.getHeight() / 2f);

			if (Intersector.intersectSegmentRectangle(lightStartPosition, lightEndPosition, obstacleBounds)) {
				isColliding = true;
				collidedObstacle = obstacle;
				break;
			}
		}
	}

	private void checkRedLightCollision() {
		isRedLightColliding = false;
		Rectangle redLightBounds = new Rectangle();
		redLightBounds.setWidth(ballSprite.getWidth());
		redLightBounds.setHeight(ballSprite.getHeight());
		redLightBounds.setCenter(redLightStartPosition);

		for (Sprite obstacle : obstacles) {
			Rectangle obstacleBounds = obstacle.getBoundingRectangle();
			obstacleBounds.setWidth(obstacleBounds.getWidth() + redLightBounds.getWidth());
			obstacleBounds.setHeight(obstacleBounds.getHeight() + redLightBounds.getHeight());
			obstacleBounds.setPosition(obstacle.getX() - redLightBounds.getWidth() / 2f, obstacle.getY() - redLightBounds.getHeight() / 2f);

			if (Intersector.intersectSegmentRectangle(lightStartPosition, lightEndPosition, obstacleBounds)) {
				isRedLightColliding = true;
				collidedObstacle = obstacle;
				break;
			}
		}
	}

	private void checkGreenLightCollision() {
		isGreenLightColliding = false;
		Rectangle greenLightBounds = new Rectangle();
		greenLightBounds.setWidth(ballSprite.getWidth());
		greenLightBounds.setHeight(ballSprite.getHeight());
		greenLightBounds.setCenter(greenLightStartPosition);

		for (Sprite obstacle : obstacles) {
			if (collidedObstacle != obstacle) {
				Rectangle obstacleBounds = obstacle.getBoundingRectangle();
				obstacleBounds.setWidth(obstacleBounds.getWidth() + greenLightBounds.getWidth());
				obstacleBounds.setHeight(obstacleBounds.getHeight() + greenLightBounds.getHeight());
				obstacleBounds.setPosition(obstacle.getX() - greenLightBounds.getWidth() / 2f, obstacle.getY() - greenLightBounds.getHeight() / 2f);

				if (Intersector.intersectSegmentRectangle(redLightStartPosition, redLightEndPosition, obstacleBounds)) {
					isGreenLightColliding = true;
					greenCollidedObstacle = obstacle;
					break;
				}
			}
		}
	}



	private void draw() {
		Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

		camera.update();
		batch.setProjectionMatrix(camera.combined);
		batch.begin();
		ballSprite.draw(batch);
		for (Sprite obstacle : obstacles) {
			obstacle.draw(batch);
		}
		batch.end();

		shapeRenderer.setProjectionMatrix(camera.combined);
		shapeRenderer.begin(ShapeRenderer.ShapeType.Line);

		float lineWidth = ballSprite.getWidth() * 0.1f; // можно регулировать коэффицент для выбора толщины линии


		shapeRenderer.setColor(1, 1, 0, 1);
		shapeRenderer.rectLine(ballPosition.x, ballPosition.y, lightEndPosition.x, lightEndPosition.y, lineWidth);


		if (isRedLightColliding) {
			shapeRenderer.setColor(1, 0, 0, 1);
			shapeRenderer.rectLine(collidedObstacle.getX() + collidedObstacle.getWidth() / 2f, collidedObstacle.getY() + collidedObstacle.getHeight() / 2f,
					redLightEndPosition.x, redLightEndPosition.y, lineWidth);
			shapeRenderer.rect(collidedObstacle.getX() - ballSprite.getWidth() / 2f,
					collidedObstacle.getY() - ballSprite.getHeight() / 2f,
					collidedObstacle.getWidth() + ballSprite.getWidth(),
					collidedObstacle.getHeight() + ballSprite.getHeight());

			//без красного света зеленый не может отображаться
			if (isGreenLightColliding) {
				shapeRenderer.setColor(0, 1, 0, 1);
				shapeRenderer.rectLine(greenCollidedObstacle.getX() + greenCollidedObstacle.getWidth() / 2f,
						greenCollidedObstacle.getY() + greenCollidedObstacle.getHeight() / 2f,
						greenLightEndPosition.x, greenLightEndPosition.y, lineWidth);
				shapeRenderer.rect(greenCollidedObstacle.getX() - ballSprite.getWidth() / 2f,
						greenCollidedObstacle.getY() - ballSprite.getHeight() / 2f,
						greenCollidedObstacle.getWidth() + ballSprite.getWidth(),
						greenCollidedObstacle.getHeight() + ballSprite.getHeight());
			}
		}
		shapeRenderer.end();
	}


	//метод для подсчета угла между двумя векторами
	private float calculateAngle(Vector2 target, Vector2 pos) {
		return (float) Math.toDegrees(Math.atan2(target.y - pos.y, target.x - pos.x));
	}

	@Override
	public void dispose() {
		batch.dispose();
		ballTexture.dispose();
		obstacleTexture.dispose();
		shapeRenderer.dispose();
	}
}