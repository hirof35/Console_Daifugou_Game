package daifugoGame;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Collectors;

// --- 1. カードクラス ---
class Card implements Comparable<Card> {
    private final String suit;
    private final int rank;
    private final int strength;
    private final boolean isJoker;

    public Card(String suit, int rank, boolean isJoker) {
        this.suit = suit;
        this.rank = rank;
        this.isJoker = isJoker;
        this.strength = isJoker ? 16 : (rank == 1 ? 14 : (rank == 2 ? 15 : rank));
    }

    public int getRank() { return rank; }
    public int getStrength() { return strength; }
    public boolean isJoker() { return isJoker; }

    @Override
    public String toString() {
        if (isJoker) return "[JOKER]";
        String r = switch(rank) { case 1 -> "A"; case 11 -> "J"; case 12 -> "Q"; case 13 -> "K"; default -> String.valueOf(rank); };
        return "[" + suit + r + "]";
    }

    @Override
    public int compareTo(Card other) {
        return Integer.compare(this.strength, other.strength);
    }
}

// --- 2. プレイヤー基底クラス ---
abstract class Player {
    protected String name;
    protected List<Card> hand = new ArrayList<>();

    public Player(String name) { this.name = name; }
    public String getName() { return name; }
    public List<Card> getHand() { return hand; }
    public void addCards(List<Card> cards) { hand.addAll(cards); Collections.sort(hand); }
    public void removeCards(List<Card> cards) { hand.removeAll(cards); }
    public abstract List<Card> think(Table table, GameLogic logic);
}

// --- 3. AIプレイヤー ---
class SimpleAI extends Player {
    public SimpleAI(String name) { super(name); }

    @Override
    public List<Card> think(Table table, GameLogic logic) {
        int required = table.isEmpty() ? 1 : table.getLastPlayedCards().size();
        // 弱い順にグループ化して、出せるものを探す
        Map<Integer, List<Card>> groups = hand.stream().collect(Collectors.groupingBy(Card::getStrength));
        List<Integer> strengths = new ArrayList<>(groups.keySet());
        Collections.sort(strengths);
        if (table.isStrengthReversed()) Collections.reverse(strengths);

        for (int s : strengths) {
            List<Card> candidate = groups.get(s);
            if (candidate.size() >= required) {
                List<Card> play = candidate.subList(0, required);
                if (logic.canPlay(play, table)) return play;
            }
        }
        return Collections.emptyList(); // パス
    }
}

// --- 4. 人間プレイヤー ---
class HumanPlayer extends Player {
    private Scanner scanner = new Scanner(System.in);
    public HumanPlayer(String name) { super(name); }

    @Override
    public List<Card> think(Table table, GameLogic logic) {
        System.out.println("\nあなたの手札: ");
        for (int i = 0; i < hand.size(); i++) System.out.print(i + ":" + hand.get(i) + " ");
        System.out.println("\n出したいカードの番号をスペース区切りで入力してください (パスはEnter):");
        
        String input = scanner.nextLine();
        if (input.isEmpty()) return Collections.emptyList();

        try {
            List<Card> selected = Arrays.stream(input.split(" "))
                .map(s -> hand.get(Integer.parseInt(s))).collect(Collectors.toList());
            if (logic.canPlay(selected, table)) return selected;
            else { System.out.println("それは出せません！"); return think(table, logic); }
        } catch (Exception e) { System.out.println("入力が正しくありません。"); return think(table, logic); }
    }
}

// --- 5. 場の管理クラス ---
class Table {
    private List<Card> lastPlayedCards;
    private boolean isRevolution = false;
    private boolean is11Back = false;
    private boolean was8Cut = false;

    public boolean isStrengthReversed() { return isRevolution ^ is11Back; }
    public void setLastPlayedCards(List<Card> cards) { this.lastPlayedCards = cards; }
    public List<Card> getLastPlayedCards() { return lastPlayedCards; }
    public void set11Back(boolean b) { this.is11Back = b; }
    public void toggleRevolution() { isRevolution = !isRevolution; }
    public void set8Cut(boolean b) { this.was8Cut = b; }
    public boolean was8Cut() { return was8Cut; }
    public boolean isEmpty() { return lastPlayedCards == null; }
    public void clear() { lastPlayedCards = null; is11Back = false; was8Cut = false; }
}

// --- 6. ロジッククラス ---
class GameLogic {
    public boolean canPlay(List<Card> selected, Table table) {
        if (selected.isEmpty()) return false;
        // 全て同じ数字かチェック
        int s0 = selected.get(0).getStrength();
        if (!selected.stream().allMatch(c -> c.getStrength() == s0)) return false;

        if (table.isEmpty()) return true;
        if (selected.size() != table.getLastPlayedCards().size()) return false;

        int lastS = table.getLastPlayedCards().get(0).getStrength();
        return table.isStrengthReversed() ? s0 < lastS : s0 > lastS;
    }

    public void applyEffects(List<Card> played, Table table) {
        int rank = played.get(0).getRank();
        if (rank == 8) table.set8Cut(true);
        if (rank == 11) table.set11Back(true);
    }
}

// --- 7. メイン実行クラス ---
public class DaifugoGame {
    public static void main(String[] args) {
        List<Player> players = new ArrayList<>(List.of(new HumanPlayer("YOU"), new SimpleAI("CPU1"), new SimpleAI("CPU2"), new SimpleAI("CPU3")));
        Table table = new Table();
        GameLogic logic = new GameLogic();
        
        // デッキ準備と配布
        List<Card> deck = new ArrayList<>();
        String[] suits = {"♠", "♥", "♦", "♣"};
        for (String s : suits) for (int r = 1; r <= 13; r++) deck.add(new Card(s, r, false));
        deck.add(new Card("JK", 0, true));
        Collections.shuffle(deck);
        for (int i = 0; i < deck.size(); i++) players.get(i % 4).addCards(List.of(deck.get(i)));

        int turn = 0;
        int passCount = 0;

        while (players.stream().filter(p -> !p.getHand().isEmpty()).count() > 1) {
            Player p = players.get(turn % 4);
            if (p.getHand().isEmpty()) { turn++; continue; }

            System.out.println("\n--------------------");
            System.out.println("番: " + p.getName() + " | 場: " + (table.isEmpty() ? "空" : table.getLastPlayedCards()));
            
            List<Card> choice = p.think(table, logic);
            if (choice.isEmpty()) {
                System.out.println(p.getName() + " はパスしました。");
                passCount++;
            } else {
                System.out.println(p.getName() + " が " + choice + " を出しました。");
                table.setLastPlayedCards(choice);
                p.removeCards(choice);
                logic.applyEffects(choice, table);
                passCount = 0;
                if (table.was8Cut()) { table.clear(); System.out.println("8切り！"); continue; }
            }

            if (passCount >= players.stream().filter(pl -> !pl.getHand().isEmpty()).count() - 1) {
                table.clear();
                passCount = 0;
                System.out.println("場が流れました。");
            } else {
                turn++;
            }
        }
        System.out.println("ゲーム終了！");
    }
}
