package edu.co.arsw.gridmaster.service;

import edu.co.arsw.gridmaster.model.User;
import edu.co.arsw.gridmaster.model.exceptions.GridMasterException;
import edu.co.arsw.gridmaster.persistance.GamePersistence;
import edu.co.arsw.gridmaster.model.Game;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GameService {

    @Autowired
    GamePersistence gamePersistence;

    // Returns game?
    public Integer createGame() throws GridMasterException {
        Game newGame = new Game();
        Integer code = newGame.getCode();
        gamePersistence.saveGame(newGame);
        return code;
    }

    public void deleteGame(Integer code) throws GridMasterException {
        gamePersistence.deleteGame(code);
    }

    public void startGame(){

    }

    public void endGame(){
        // void method for the moment
    }

    public Set<Game> getAllGames(){
        return gamePersistence.getAllGames();
    }

    public Game getGameByCode(Integer code) throws GridMasterException {
        return gamePersistence.getGameByCode(code);
    }

    public void addUser(Integer code, User user) throws GridMasterException {
        Game game = gamePersistence.getGameByCode(code);
        // Obtain the color
        game.addUser(user);
        gamePersistence.saveGame(game);
    }

    public ConcurrentHashMap<String, Integer> getScores(Integer code) throws GridMasterException {
        Game game = gamePersistence.getGameByCode(code);
        return game.getScores();
    }

}