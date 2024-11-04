package edu.co.arsw.gridmaster.service;

import edu.co.arsw.gridmaster.model.Box;
import edu.co.arsw.gridmaster.model.GameState;
import edu.co.arsw.gridmaster.model.GridMaster;
import edu.co.arsw.gridmaster.model.Player;
import edu.co.arsw.gridmaster.model.exceptions.*;
import edu.co.arsw.gridmaster.persistance.GridMasterPersistence;
import edu.co.arsw.gridmaster.persistance.Tuple;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class GridMasterService {

    @Autowired
    GridMasterPersistence gridMasterPersistence;

    public Set<GridMaster> getAllGames(){
        return gridMasterPersistence.getAllGames();
    }

    public GridMaster getGameByCode(Integer code) throws GridMasterException {
        return gridMasterPersistence.getGameByCode(code);
    }

    public ArrayList<Player> getPlayers(Integer code) throws GridMasterException {
        GridMaster game = gridMasterPersistence.getGameByCode(code);
        return new ArrayList<>(game.getPlayers().values());
    }

    public Player getPlayerByName(Integer code, String name) throws GridMasterException {
        GridMaster game = gridMasterPersistence.getGameByCode(code);
        Player player = game.getPlayerByName(name);
        if(player == null){
            throw new PlayerNotFoundException();
        }
        return player;
    }

    public Map<String, Integer> getScoreboard(Integer code) throws GridMasterException {
        GridMaster game = gridMasterPersistence.getGameByCode(code);
        ConcurrentHashMap<String, Integer> scores = game.getScores();
        Map<String, Integer> newScores = scores.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
        return newScores;
    }

    public Integer createGridMaster() throws GridMasterException {
        GridMaster newGame = new GridMaster();
        gridMasterPersistence.saveGame(newGame);
        return newGame.getCode();
    }

    public void startGame(Integer code) throws GridMasterException {
        GridMaster game = gridMasterPersistence.getGameByCode(code);
        game.setGameState(GameState.STARTED);
        startTime(game);
        setPositions(game);
    }

    public void startTime(GridMaster game){
        Timer timer = new Timer();
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                game.decrementTime();
                if(game.getTime() < 0){
                    timer.cancel();
                    try {
                        endGame(game.getCode());
                    } catch (GridMasterException e) {
                        System.out.println("Error finishing game.");
                    }
                }
                try {
                    gridMasterPersistence.saveGame(game);
                } catch (GridMasterException e) {
                    System.out.println("Error decrementing time.");
                }
            }
        };

        timer.scheduleAtFixedRate(task, 0, 1000);
    }

    public String getTime(Integer code) throws GridMasterException {
        GridMaster game = gridMasterPersistence.getGameByCode(code);
        int time = game.getTime();
        int minutes = time / 60;
        int seconds = time % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    public void endGame(Integer code) throws GridMasterException{
        GridMaster game = gridMasterPersistence.getGameByCode(code);
        game.setGameState(GameState.FINISHED);
    }

    public void setPositions(GridMaster game) throws GridMasterException {
        ArrayList<int[]> positions = new ArrayList<>();
        int[] position;
        for(Player i : game.getPlayers().values()){
            do {
                i.generatePosition(game.getDimension().getFirst(), game.getDimension().getSecond());
                position = i.getPosition();
            } while (positions.contains(position));
            positions.add(position);
            game.getBox(new Tuple<>(position[0], position[1])).setBusy(true);
        }
        gridMasterPersistence.saveGame(game);
    }

    public void addPlayer(Integer code, String name) throws GridMasterException {
        GridMaster game = gridMasterPersistence.getGameByCode(code);
        if(game.getMaxPlayers() == game.getPlayers().size()){
            throw new GameException("Room is full.");
        }
        if(game.getPlayerByName(name) != null){
            throw new PlayerSaveException();
        }
        Player player = new Player(name);
        player.setColor(game.obtainColor());
        game.addPlayer(player);
        if(game.getGameState().equals(GameState.STARTED)){
            while(true){
                Integer x = game.getDimension().getFirst();
                Integer y = game.getDimension().getSecond();
                player.generatePosition(x, y);
                int[] position = player.getPosition();
                Box box = game.getBox(new Tuple<>(position[0], position[1]));
                synchronized (box){
                    if(!box.isBusy()){
                        box.setBusy(true);
                        break;
                    }
                }
            }
        }
        gridMasterPersistence.saveGame(game);
    }

    public void move(Integer code, String playerName, Tuple<Integer, Integer> newPosition) throws GridMasterException {
        GridMaster game = gridMasterPersistence.getGameByCode(code);
        Integer x = newPosition.getFirst();
        Integer y = newPosition.getSecond();
        if(x < 0 || y < 0 || x >= game.getDimension().getFirst() || y >= game.getDimension().getSecond()){
            throw new BoardException("Invalid move.");
        }
        Player player = game.getPlayerByName(playerName);
        Tuple<Integer, Integer> oldPosition = new Tuple<>(player.getPosition()[0], player.getPosition()[1]);
        changeScore(game, player, game.getBox(newPosition), game.getBox(oldPosition));
        // game.printBoard();
        gridMasterPersistence.saveGame(game);
    }

    public void changeScore(GridMaster game, Player player, Box newBox, Box oldBox){
        // Just locking the box
        synchronized (newBox){
            // The box is free and nobody is standing there
            if(!newBox.isBusy()){
                player.setPosition(newBox.getPosition());

                player.addToTrace(newBox.getPosition());
                //System.out.println("CANTIDAD DE CASILLAS " + player.getName() + ": " + player.getTrace().size());

                player.incrementScore();
                game.updateScoreOfPlayer(player.getName(), player.getScore().get());

                oldBox.setBusy(false);
                oldBox.setOwner(player);
                oldBox.setColor(player.getColor());

                newBox.setBusy(true);
                // Decrementing opponent score

                if(newBox.getOwner() != null){
                    Player opponent = newBox.getOwner();

                    opponent.removeFromTrace(newBox.getPosition());
                    //System.out.println("CANTIDAD DE CASILLAS " + opponent.getName() + ": " + player.getTrace().size());

                    opponent.decrementScore();
                    game.updateScoreOfPlayer(opponent.getName(), opponent.getScore().get());
                }
            }
        }
    }

    public void deleteGridMaster(Integer code) throws GridMasterException{
        gridMasterPersistence.deleteGame(code);
    }

}
