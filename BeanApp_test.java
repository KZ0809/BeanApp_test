import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableModel;

import java.awt.*;
import java.awt.event.*;
import java.sql.*;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.axis.NumberAxis; 
import java.text.DecimalFormat;

public class BeanApp_test extends JFrame {
    //DBへの接続環境作成
    private static final String POSTGRES_DRIVER = "org.postgresql.Driver";
    private static final String JDBC_CONNECTION = "jdbc:postgresql://localhost:5432/postgres";
    private static final String USER = "postgres";
    private static final String PASS = "Kazu0816";


    //mainPanelのインスタンス
    private CardLayout cardLayout;
    private JPanel mainPanel;
    private JList<String> beanList;
    private DefaultListModel<String> beanListModel;
    private JTextField nameField, dateField, roasterField, storeField, weatherField, tempField, humidField, inputField, outputField, indexField, degreeField, bottomField;
    private Connection conn;
    private boolean isUpdateMode = false;
    private String currentSelectedName = ""; // 現在選択中の豆の名前（リストからの選択や編集対象）

    //viewPanelのインスタンス
    private JPanel viewPanel;
    private JLabel viewNameLabel, viewDateField, viewRoasterLabel, viewStoreLabel, viewWeatherLabel, viewTempLabel, viewHumidLabel, viewInputLabel, viewOutputLabel, viewIndexLabel, viewDegreeLabel, viewBottomLabel;
    DefaultTableModel roastTableModel; 
    private JTable roastTable;
    private ChartPanel chartPanel;

    // RoastPanelのインスタンス
    private RoastPanel roastPanel;

    //アプリの大枠の作成
    public BeanApp_test() {

        //メインパネルの定義
        setTitle("BeanApp");
        setSize(400, 500); 
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);


        //データベース接続とテーブル定義
        try {
            Class.forName(POSTGRES_DRIVER);
            conn = DriverManager.getConnection(JDBC_CONNECTION, USER, PASS);
            try (Statement stmt = conn.createStatement()) {
                // beansテーブルの定義
                // PRIMARY KEYであるnameカラム以外はNOT NULL制約を削除
                stmt.execute("DROP TABLE IF EXISTS beans CASCADE");
                stmt.execute("CREATE TABLE IF NOT EXISTS beans (" +
                             "name TEXT PRIMARY KEY, " +
                             "roast_date TEXT, " +     // NULLを許容
                             "roaster TEXT, " +        // NULLを許容
                             "store_roast TEXT, " +    // NULLを許容
                             "weather TEXT, " +        // NULLを許容
                             "temperature DOUBLE PRECISION, " + // 数値型に戻す
                             "humidity DOUBLE PRECISION, " +   // 数値型に戻す
                             "input_weight DOUBLE PRECISION, " + // NULLを許容
                             "output_weight DOUBLE PRECISION, " + // NULLを許容
                             "roast_Index TEXT, " +    // NULLを許容
                             "roast_Degree TEXT, " +   // NULLを許容
                             "bottom DOUBLE PRECISION)");          // bottomをDOUBLE PRECISIONに戻す

                // roast_tableが存在しない場合は作成
                stmt.execute("DROP TABLE IF EXISTS roast_table"); // 古いテーブルを削除（CASCADEで削除済みの可能性もあるが念のため）
                stmt.execute("CREATE TABLE IF NOT EXISTS roast_table (" +
                    "roast_id SERIAL PRIMARY KEY," + // 焙煎テーブル自体のプライマリキー
                    "bean_name TEXT NOT NULL REFERENCES beans(name) ON DELETE CASCADE," + // 豆の名前を外部キーとして追加 (NOT NULLは維持)
                    "time_minute INTEGER," +
                    "temperature DOUBLE PRECISION," +
                    "temp_change DOUBLE PRECISION," +
                    "gas_pressure TEXT," +
                    "damper TEXT," +
                    "note TEXT" +
                ")");
            }
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Database connection failed.");
            System.exit(1);
        }


        //スタート画面の作成
        JPanel startPanel = new JPanel(new BorderLayout());
        JPanel buttonPanel = new JPanel(new GridLayout(2, 1, 10, 10));
        JButton toCreateButton = new JButton("新規作成");
        JButton toListButton = new JButton("一覧");
        toCreateButton.setFont(new Font("Dialog", Font.PLAIN, 16));
        toListButton.setFont(new Font("Dialog", Font.PLAIN, 16));
        buttonPanel.add(toCreateButton);
        buttonPanel.add(toListButton);
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(20, 80, 20, 80));

        //アイコン表示
        ImageIcon icon = new ImageIcon("coffee_icon.png");
        Image scaledImage = icon.getImage().getScaledInstance(250, 250, Image.SCALE_SMOOTH);
        JLabel iconLabel = new JLabel(new ImageIcon(scaledImage));
        iconLabel.setHorizontalAlignment(JLabel.CENTER);

        JPanel iconContainer = new JPanel(new BorderLayout());
        iconContainer.add(iconLabel, BorderLayout.NORTH);
        iconContainer.add(buttonPanel, BorderLayout.CENTER);
        //アイコンの定義終了

        startPanel.add(iconContainer, BorderLayout.CENTER);//アイコンをスタート画面に追加
        mainPanel.add(startPanel, "Menu");//スタート画面をメインパネルに追加
        //スタート画面作成終了        


        //新規作成画面の作成
        JPanel createPanel = new JPanel(new BorderLayout());
        JPanel scrollableInputPanel = new JPanel(new GridLayout(14, 1, 5, 5));
        //スクロール可能に
        JScrollPane scrollPane = new JScrollPane(scrollableInputPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        //各項目の定義
        nameField = new JTextField();//豆の名前
        dateField = new JTextField(); //日付
        roasterField = new JTextField();//焙煎者の名前
        storeField = new JTextField();//焙煎場所
        weatherField = new JTextField();//天気
        tempField = new JTextField();//気温
        humidField = new JTextField();//湿度
        inputField = new JTextField();//投入量
        outputField = new JTextField();//引き上げ量
        indexField = new JTextField();//焙煎指数
        degreeField = new JTextField();//焙煎度合い
        bottomField = new JTextField();//ボトム

        scrollableInputPanel.add(new JLabel("生豆の名前："));
        scrollableInputPanel.add(nameField);
        scrollableInputPanel.add(new JLabel("焙煎日："));
        scrollableInputPanel.add(dateField);
        scrollableInputPanel.add(new JLabel("焙煎者：")); 
        scrollableInputPanel.add(roasterField);
        scrollableInputPanel.add(new JLabel("焙煎場所："));
        scrollableInputPanel.add(storeField);
        scrollableInputPanel.add(new JLabel("天気："));
        scrollableInputPanel.add(weatherField);
        scrollableInputPanel.add(new JLabel("気温："));
        scrollableInputPanel.add(tempField);
        scrollableInputPanel.add(new JLabel("湿度："));
        scrollableInputPanel.add(humidField);
        scrollableInputPanel.add(new JLabel("投入量(g)："));
        scrollableInputPanel.add(inputField);
        scrollableInputPanel.add(new JLabel("引き上げ量(g)："));
        scrollableInputPanel.add(outputField);
        scrollableInputPanel.add(new JLabel("焙煎指数："));
        scrollableInputPanel.add(indexField);
        scrollableInputPanel.add(new JLabel("焙煎度合い："));
        scrollableInputPanel.add(degreeField);
        scrollableInputPanel.add(new JLabel("ボトム："));
        scrollableInputPanel.add(bottomField);
        //項目定義づけ終了


        // 下部のボタン部分作成
        JButton saveButton = new JButton("保存");
        JButton roastButton = new JButton("焙煎表へ");
        JButton backButton = new JButton("戻る");
        //ボタンを一つに纏める
        JPanel buttonPanel_2 = new JPanel(new FlowLayout());
        buttonPanel_2.add(saveButton);
        buttonPanel_2.add(roastButton);
        buttonPanel_2.add(backButton);

        // 画面に追加
        //ボタンは必ず下部に来るようにスクロールパネルとは別で作成
        createPanel.add(scrollPane, BorderLayout.CENTER);
        createPanel.add(buttonPanel_2, BorderLayout.SOUTH);

        //メインパネルへの追加
        mainPanel.add(createPanel, "Create");
        //新規作成画面作成終了


        //一覧画面の作成
        JPanel listPanel = new JPanel(new BorderLayout());
        beanListModel = new DefaultListModel<>();
        beanList = new JList<>(beanListModel);
        //スクロール可能に
        JScrollPane listScrollPane = new JScrollPane(beanList);

        //ボタン部分作成
        JButton listBackButton = new JButton("戻る");

        //パネルに追加
        listPanel.add(listScrollPane, BorderLayout.CENTER);
        JPanel listButtons = new JPanel();
        //ボタンが下部に来るように配置
        listButtons.add(listBackButton);
        listPanel.add(listButtons, BorderLayout.SOUTH);

        //メインパネルへの追加
        mainPanel.add(listPanel, "List");
        //一覧画面の作成終了


        //登録した豆を見る画面
        //豆の情報を出すパネル
        viewPanel = new JPanel(new BorderLayout());
        JPanel beanInfoPanel = new JPanel(new GridLayout(13, 1)); 
        // 焙煎表とグラフをまとめてスクロール可能にするためのパネル
        JPanel scrollableContentPanel = new JPanel();
        scrollableContentPanel.setLayout(new BoxLayout(scrollableContentPanel, BoxLayout.Y_AXIS));

        //豆の情報を表示
        viewNameLabel = new JLabel();
        viewDateField = new JLabel();
        viewRoasterLabel = new JLabel();
        viewStoreLabel = new JLabel();
        viewWeatherLabel = new JLabel();
        viewTempLabel = new JLabel();
        viewHumidLabel = new JLabel();
        viewInputLabel = new JLabel();
        viewOutputLabel = new JLabel();
        viewIndexLabel = new JLabel();
        viewDegreeLabel = new JLabel();
        viewBottomLabel = new JLabel();

        beanInfoPanel.add(viewNameLabel);
        beanInfoPanel.add(viewDateField); 
        beanInfoPanel.add(viewRoasterLabel);
        beanInfoPanel.add(viewStoreLabel);
        beanInfoPanel.add(viewWeatherLabel);
        beanInfoPanel.add(viewTempLabel);
        beanInfoPanel.add(viewHumidLabel);
        beanInfoPanel.add(viewInputLabel);
        beanInfoPanel.add(viewOutputLabel);
        beanInfoPanel.add(viewIndexLabel);
        beanInfoPanel.add(viewDegreeLabel);
        beanInfoPanel.add(viewBottomLabel);
        viewPanel.add(beanInfoPanel, BorderLayout.NORTH); // 豆情報は常に上に表示
        

        // 下部に焙煎表を追加
        roastTableModel = new DefaultTableModel(new Object[]{"時間 (分)", "温度", "温度変化", "ガス圧", "ダンパー", "メモ"}, 0);
        roastTable = new JTable(roastTableModel);
        //焙煎表自体もスクロール可能に
        JScrollPane roastScrollPane = new JScrollPane(roastTable);
        roastScrollPane.setPreferredSize(new Dimension(350, 200)); 
        roastScrollPane.setAlignmentX(Component.LEFT_ALIGNMENT);
        //焙煎表をスクロールパネルに追加
        scrollableContentPanel.add(roastScrollPane);


        //データロード前のグラフパネルの定義
        XYSeriesCollection initialDataset = new XYSeriesCollection();
        JFreeChart initialChart = ChartFactory.createXYLineChart(
            "焙煎温度プロファイル", // グラフタイトル
            "時間 (分)",            // X軸ラベル
            "温度 (℃)",             // Y軸ラベル
            initialDataset           // データセット
        );
        chartPanel = new ChartPanel(initialChart);
        chartPanel.setPreferredSize(new Dimension(350, 200));
        chartPanel.setAlignmentX(Component.LEFT_ALIGNMENT); 
        scrollableContentPanel.add(chartPanel);


        // 焙煎表、グラフの配置
        JScrollPane mainContentScrollPane = new JScrollPane(scrollableContentPanel);
        mainContentScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        mainContentScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        viewPanel.add(mainContentScrollPane, BorderLayout.CENTER);


        // ボタン類の配置
        JPanel viewButtons = new JPanel();
        JButton viewBackButton = new JButton("戻る");
        JButton editButton = new JButton("編集");
        //ボタンは纏めて下部に配置
        viewButtons.add(viewBackButton);
        viewButtons.add(editButton);
        viewPanel.add(viewButtons, BorderLayout.SOUTH);

        //メインパネルへの追加
        mainPanel.add(viewPanel, "View");
        //登録した豆を見る画面作成終了


        //焙煎表のパネルを作成
        roastPanel = new RoastPanel(conn, cardLayout, mainPanel, this);
        

        //メインパネルへの追加
        mainPanel.add(roastPanel, "Roast");
        //焙煎表のパネル作成終了

        //メインパネルの追加
        add(mainPanel);
        cardLayout.show(mainPanel, "Menu");
        //パネル作成関連作成終了

        //ここからは各ボタンの定義

        //スタート画面
        //新規作成ボタンの定義
        toCreateButton.addActionListener(e -> {
            isUpdateMode = false;
            clearFields();
            cardLayout.show(mainPanel, "Create");//新規作成画面へと移動
        });

        //一覧ボタンの定義
        toListButton.addActionListener(e -> {
            loadBeans();
            cardLayout.show(mainPanel, "List");//一覧画面へと移動
        });
        //スタート画面ボタン定義終了


        //新規作成画面ボタン定義
        //戻るボタン
        backButton.addActionListener(e -> cardLayout.show(mainPanel, "Menu"));//スタート画面へ移動
        // 焙煎表へボタンのアクションリスナー
        roastButton.addActionListener(e -> {
            String beanName = nameField.getText().trim();
            //まずは生豆の名前を登録しているか確認、していないなら登録するように誘導
            if (beanName.isEmpty()) {
                JOptionPane.showMessageDialog(this, "焙煎表を作成・編集するには、まず生豆の名前を入力してください。", "情報", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            //豆の名前が登録されている場合、それが保存されているかを判断。されていないなら保存するように誘導
            try {
                if (!beanExists(beanName)) {
                    int confirm = JOptionPane.showConfirmDialog(this,
                        "「" + beanName + "」の豆情報はまだ保存されていません。先に豆情報を保存しますか？",
                        "豆情報未保存", JOptionPane.YES_NO_OPTION);
                    if (confirm == JOptionPane.YES_OPTION) {
                        if (saveBeanInternal()) { // 内部的に豆情報を保存
                            currentSelectedName = beanName; // 新規作成の場合、currentSelectedNameを更新
                            cardLayout.show(mainPanel, "Roast"); // 保存成功したら焙煎表へ
                        } else {
                            // saveBeanInternal内でエラーメッセージが表示される
                        }
                    } else {
                        // ユーザーが保存をキャンセルした場合
                        return;
                    }
                } else {
                    // 豆情報が既に存在する場合、currentSelectedNameを更新
                    currentSelectedName = beanName;
                    cardLayout.show(mainPanel, "Roast");
                }
            } catch (SQLException ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "豆情報の確認中にエラーが発生しました。", "エラー", JOptionPane.ERROR_MESSAGE);
            }
        });

        //保存ボタンの定義
        saveButton.addActionListener(e -> saveBean());
        //新規作成画面ボタン定義終了


        //一覧画面ボタン定義
        //戻るボタン
        listBackButton.addActionListener(e -> cardLayout.show(mainPanel, "Menu"));//スタート画面へ移動

        //ダブルクリックの定義
        beanList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent evt) {
                if (evt.getClickCount() == 2) {
                    String selected = beanList.getSelectedValue();
                    if (selected != null) {
                        currentSelectedName = selected; // 選択された豆の名前を記録
                        showViewPanel(selected);
                        cardLayout.show(mainPanel, "View");//選択された豆の詳細情報の画面へ
                    }
                }
            }
        });

        //一覧画面ボタン定義終了


        //豆の詳細画面ボタン定義
        viewBackButton.addActionListener(e -> cardLayout.show(mainPanel, "List"));//一覧画面に移動

        //編集ボタンの定義
        editButton.addActionListener(e -> {
            if (!currentSelectedName.isEmpty()) {
                isUpdateMode = true;
                populateFields(currentSelectedName);
                cardLayout.show(mainPanel, "Create");//新規作成画面へと移動、豆の乗法変更を可能に
            }
        });

        //ボタンの定義終了
    }

    // 焙煎パネルがどの豆に関連付けられているかを取得するためのゲッター
    public String getCurrentSelectedBeanNameForRoast() {
        // currentSelectedNameが設定されていればそれを優先
        if (currentSelectedName != null && !currentSelectedName.isEmpty()) {
            return currentSelectedName;
        }
        // 新規作成モードで、nameFieldに何か入力されている場合はその名前を優先
        if (!nameField.getText().trim().isEmpty()) {
            return nameField.getText().trim();
        }
        return null; // どちらも不明な場合
    }

    // 豆がデータベースに存在するかどうかを確認するヘルパーメソッド
    private boolean beanExists(String beanName) throws SQLException {
        PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM beans WHERE name = ?");
        ps.setString(1, beanName);
        ResultSet rs = ps.executeQuery();
        return rs.next() && rs.getInt(1) > 0;
    }

    // 焙煎表のデータから最低温度を計算し、bottomFieldに設定するメソッド
    public void calculateAndSetBottom() {
        Double minTemp = null; // 最低値を保持する変数

        // RoastPanelのtableModelからデータを取得
        if (roastPanel != null && roastPanel.tableModel != null) {
            RoastTableModel model = roastPanel.tableModel;
            for (int i = 0; i < model.getRowCount(); i++) {//焙煎表の２行目である温度を取得
                Object tempValueObject = model.getValueAt(i, 1);

                if (tempValueObject != null) {//nullでないかの確認
                    String tempString = tempValueObject.toString().trim();

                    if (!tempString.isEmpty()) {//取得したデータが全てnullでないかどうかを確認
                        try {
                            double currentTemp = Double.parseDouble(tempString);

                            // NaNや無限大は最低値として扱わない
                            if (!Double.isNaN(currentTemp) && !Double.isInfinite(currentTemp)) {
                                if (minTemp == null || currentTemp < minTemp) {
                                    minTemp = currentTemp; // 現在の温度がこれまでの最低値より小さければ更新
                                }
                            }
                        } catch (NumberFormatException e) {
                            // 数値に変換できない値は無視する
                        }
                    }
                }
            }
        }
        
        // 計算された最低温度をbottomFieldに設定
        if (minTemp != null) {
            bottomField.setText(String.format("%.2f", minTemp)); // 2桁の小数点以下でフォーマット
        } else {
            bottomField.setText(""); // 有効な温度データがなければクリア
        }
    }

    // 従来のsaveBeanメソッドは、saveBeanInternalを呼び出し、その後に画面遷移を行う
    private void saveBean() {
        if (saveBeanInternal()) {
            clearFields(); // 保存成功後、フィールドをクリア
            cardLayout.show(mainPanel, "Menu"); // 保存成功時のみメニューに戻る
        }
    }


    // 豆情報を保存する内部メソッド（成功したらtrueを返す）
    private boolean saveBeanInternal() {
        try {
            String beanName = nameField.getText().trim();//名前を取得
            if (beanName.isEmpty()) {//もしも名前がないならエラー
                JOptionPane.showMessageDialog(this, "生豆の名前は必須です。", "エラー", JOptionPane.ERROR_MESSAGE);
                return false;
            }

            // 各JTextFieldから値を取得し、空文字列の場合はnullに変換
            // 数値以外を許容するTEXT型のフィールド
            String roastDate = dateField.getText().trim().isEmpty() ? null : dateField.getText().trim();
            String roaster = roasterField.getText().trim().isEmpty() ? null : roasterField.getText().trim();
            String storeRoast = storeField.getText().trim().isEmpty() ? null : storeField.getText().trim();
            String weather = weatherField.getText().trim().isEmpty() ? null : weatherField.getText().trim();

            // 数値を期待するDOUBLE PRECISION型のフィールド
            double temperature;
            try {//気温が数値かどうかを判定する。数値でないならエラー
                temperature = tempField.getText().trim().isEmpty() ? 0.0 : Double.parseDouble(tempField.getText().trim());
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "気温には数値を入力してください。", "入力エラー", JOptionPane.ERROR_MESSAGE);
                return false;
            }

            double humidity;
            try {//湿度が数値かどうかを判定する。数値でないならエラー
                humidity = humidField.getText().trim().isEmpty() ? 0.0 : Double.parseDouble(humidField.getText().trim());
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "湿度には数値を入力してください。", "入力エラー", JOptionPane.ERROR_MESSAGE);
                return false;
            }
            
            double inputWeight;
            try {//投入温度が数値かどうかを判定する。数値でないならエラー
                inputWeight = inputField.getText().trim().isEmpty() ? 0.0 : Double.parseDouble(inputField.getText().trim());
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "投入量には数値を入力してください。", "入力エラー", JOptionPane.ERROR_MESSAGE);
                return false;
            }

            double outputWeight;
            try {//引き上げ量が数値かどうかを判定する。数値でないならエラー
                outputWeight = outputField.getText().trim().isEmpty() ? 0.0 : Double.parseDouble(outputField.getText().trim());
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "引き上げ量には数値を入力してください。", "入力エラー", JOptionPane.ERROR_MESSAGE);
                return false;
            }

            double bottomValue;
            try {//ボトムが数値かどうかを判定する。数値でないならエラー
                bottomValue = bottomField.getText().trim().isEmpty() ? 0.0 : Double.parseDouble(bottomField.getText().trim());
            } catch (NumberFormatException e) {
                 JOptionPane.showMessageDialog(this, "ボトムには数値を入力してください。", "入力エラー", JOptionPane.ERROR_MESSAGE);
                 return false;
            }

            double index = caluculateIndex(inputWeight, outputWeight);//焙煎指数を計算
            String roastIndex = String.format("%.2f", index);//焙煎指数を記入
            String roastDegree = decideRoast(index);//焙煎度合いを判定、記入

            //取得した情報を書き込む
            if (isUpdateMode) {
                PreparedStatement ps = conn.prepareStatement(
                    "UPDATE beans SET roast_date=?, roaster=?, store_roast=?, weather=?, temperature=?, humidity=?, input_weight=?, output_weight=?, roast_Index=?, roast_Degree=?, bottom=? WHERE name=?"
                );
                ps.setString(1, roastDate);
                ps.setString(2, roaster);
                ps.setString(3, storeRoast);
                ps.setString(4, weather);
                ps.setDouble(5, temperature); 
                ps.setDouble(6, humidity); 
                ps.setDouble(7, inputWeight);
                ps.setDouble(8, outputWeight);
                ps.setString(9, roastIndex);
                ps.setString(10, roastDegree);
                ps.setDouble(11, bottomValue);
                ps.setString(12, beanName); 
                ps.executeUpdate();
                isUpdateMode = false; // 更新モードを解除
            } else {
                // 新規作成の場合、同じ名前の豆が既に存在するか確認
                if (beanExists(beanName)) {
                    JOptionPane.showMessageDialog(this, "「" + beanName + "」という名前の豆はすでに存在します。", "エラー", JOptionPane.ERROR_MESSAGE);
                    return false;
                }

                //DBに新規保存する
                PreparedStatement ps = conn.prepareStatement("INSERT INTO beans VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"); // 12個の?
                ps.setString(1, beanName);
                ps.setString(2, roastDate);
                ps.setString(3, roaster);
                ps.setString(4, storeRoast);
                ps.setString(5, weather);
                ps.setDouble(6, temperature); 
                ps.setDouble(7, humidity);   
                ps.setDouble(8, inputWeight);
                ps.setDouble(9, outputWeight);
                ps.setString(10, roastIndex);
                ps.setString(11, roastDegree); 
                ps.setDouble(12, bottomValue); 
                ps.executeUpdate();
            }
            JOptionPane.showMessageDialog(this, "豆情報が保存されました！");
            return true; // 保存成功
        } catch (SQLException e) {//その他のエラー
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "豆情報の保存に失敗しました。", "エラー", JOptionPane.ERROR_MESSAGE);
            return false; // 保存失敗
        }
        // NumberFormatExceptionは各parseDouble内で個別にハンドリング
    }

    //フィールドの初期化
    private void clearFields() {
        nameField.setText("");
        dateField.setText(""); 
        roasterField.setText(""); 
        storeField.setText("");
        weatherField.setText("");
        tempField.setText("");
        humidField.setText("");
        inputField.setText("");
        outputField.setText("");
        indexField.setText("");
        degreeField.setText("");
        bottomField.setText("");
    }

    //豆情報のロード
    private void loadBeans() {
        //豆情報が残らないように空にする
        beanListModel.clear();
        try {
            Statement stmt = conn.createStatement();//Statementオブジェクトの作成
            ResultSet rs = stmt.executeQuery("SELECT name FROM beans ORDER BY name");
            while (rs.next()) {//豆情報の読み取り
                beanListModel.addElement(rs.getString("name"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "豆リストの読み込みに失敗しました。", "エラー", JOptionPane.ERROR_MESSAGE);
        }
    }

    //データの取得、表示 (新しいフィールドもロード)
    private void populateFields(String beanName) {
        try {//クエリの実行
            PreparedStatement ps = conn.prepareStatement("SELECT * FROM beans WHERE name = ?");
            ps.setString(1, beanName);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {//それぞれの情報をロード
                nameField.setText(rs.getString("name"));
                dateField.setText(rs.getString("roast_date")); 
                roasterField.setText(rs.getString("roaster")); 
                storeField.setText(rs.getString("store_roast"));
                weatherField.setText(rs.getString("weather"));
                
                // 数値型として取得し、文字列に変換してセット
                // nullの場合に"0.0"ではなく空文字にする
                double tempVal = rs.getDouble("temperature");
                tempField.setText(rs.wasNull() ? "" : String.valueOf(tempVal)); 

                double humidVal = rs.getDouble("humidity");
                humidField.setText(rs.wasNull() ? "" : String.valueOf(humidVal)); 

                double inputVal = rs.getDouble("input_weight");
                inputField.setText(rs.wasNull() ? "" : String.valueOf(inputVal));

                double outputVal = rs.getDouble("output_weight");
                outputField.setText(rs.wasNull() ? "" : String.valueOf(outputVal));

                double input_weight = rs.getDouble("input_weight");

                double output_weight = rs.getDouble("output_weight");

                String roastIndex = rs.getString("roast_Index");

                String roastDegree = rs.getString("roast_Degree");

                // もしDBにデータがない場合（旧データなど）、再計算して設定
                if (roastIndex == null || roastIndex.isEmpty() || roastDegree == null || roastDegree.isEmpty()) {
                     double calculatedIndex = caluculateIndex(input_weight, output_weight);
                     roastIndex = String.format("%.2f", calculatedIndex);
                     roastDegree = decideRoast(calculatedIndex);
                }

                //取得した数値もしくは再計算したものを記入
                indexField.setText(roastIndex);

                degreeField.setText(roastDegree);

                double bottomVal = rs.getDouble("bottom");
                bottomField.setText(rs.wasNull() ? "" : String.valueOf(bottomVal));

            }
        } catch (SQLException e) {//その他のエラー
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "豆情報の読み込みに失敗しました。", "エラー", JOptionPane.ERROR_MESSAGE);
        }
    }


    //焙煎指数の計算
    public static double caluculateIndex(double input_weight, double output_weight){
        double index = input_weight != 0 && output_weight != 0 ? input_weight / output_weight : 0.0;
        return index;
    }

    //焙煎度合いの判定
    public static String decideRoast(double index){
        String roastDegree;
        if (index <= 1.10) roastDegree = "ライトロースト";
        else if (index <= 1.125) roastDegree = "シナモンロースト";
        else if (index <= 1.15) roastDegree = "ミディアムロースト";
        else if (index <= 1.175) roastDegree = "ハイロースト";
        else if (index <= 1.20) roastDegree = "シティロースト";
        else if (index <= 1.225) roastDegree = "フルシティロースト";
        else if (index <= 1.25) roastDegree = "フレンチロースト";
        else roastDegree = "イタリアンロースト";
        return roastDegree;
    }


    //取得したデータの表示
    private void showViewPanel(String beanName) {
        try {
            // beans テーブルのデータを表示
            PreparedStatement ps = conn.prepareStatement("SELECT * FROM beans WHERE name = ?");
            ps.setString(1, beanName);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {//豆の情報の表示
                viewNameLabel.setText("生豆の名前： " + rs.getString("name"));
                viewDateField.setText("焙煎日： " + (rs.getString("roast_date") != null ? rs.getString("roast_date") : ""));
                viewRoasterLabel.setText("焙煎者： " + (rs.getString("roaster") != null ? rs.getString("roaster") : ""));
                viewStoreLabel.setText("保存場所： " + (rs.getString("store_roast") != null ? rs.getString("store_roast") : ""));
                viewWeatherLabel.setText("天気： " + (rs.getString("weather") != null ? rs.getString("weather") : ""));
                
                // 数値型として取得し、表示
                double tempVal = rs.getDouble("temperature");
                viewTempLabel.setText("気温： " + (rs.wasNull() ? "" : String.valueOf(tempVal)));
                double humidVal = rs.getDouble("humidity");
                viewHumidLabel.setText("湿度： " + (rs.wasNull() ? "" : String.valueOf(humidVal)));
                double inputVal = rs.getDouble("input_weight");
                viewInputLabel.setText("投入量： " + (rs.wasNull() ? "" : String.valueOf(inputVal)) + " g");
                double outputVal = rs.getDouble("output_weight");
                viewOutputLabel.setText("引き上げ量： " + (rs.wasNull() ? "" : String.valueOf(outputVal)) + " g");
                double bottomVal = rs.getDouble("bottom");
                viewBottomLabel.setText("ボトム： " + (rs.wasNull() ? "" : String.valueOf(bottomVal)));
                viewIndexLabel.setText("ロースト指数： " + (rs.getString("roast_Index") != null ? rs.getString("roast_Index") : ""));
                viewDegreeLabel.setText("焙煎度合い： " + (rs.getString("roast_Degree") != null ? rs.getString("roast_Degree") : ""));
            } else {//その他のエラー
                JOptionPane.showMessageDialog(this, "豆データが見つかりません。", "エラー", JOptionPane.ERROR_MESSAGE);
            }


            //グラフの表示準備
            roastTableModel.setRowCount(0); // テーブルをクリア
            XYSeries series = new XYSeries("温度"); // 系列名
            XYSeriesCollection dataset = new XYSeriesCollection();

            PreparedStatement psRoast = conn.prepareStatement("SELECT time_minute, temperature, temp_change, gas_pressure, damper, note FROM roast_table WHERE bean_name = ? ORDER BY time_minute");
            psRoast.setString(1, beanName);
            ResultSet rsRoast = psRoast.executeQuery();

            while (rsRoast.next()) {//焙煎表の表示
                int time = rsRoast.getInt("time_minute");
                double temp = rsRoast.getDouble("temperature");
                boolean tempWasNull = rsRoast.wasNull(); //気温がnullかの判定
                double tempChange = rsRoast.getDouble("temp_change");
                String gasPressure = rsRoast.getString("gas_pressure");
                String damper = rsRoast.getString("damper");
                String note = rsRoast.getString("note");
                roastTableModel.addRow(new Object[]{time, temp, tempChange, gasPressure, damper, note});
                
                //グラフデータに追加
                if (!tempWasNull && !Double.isNaN(temp) && !Double.isInfinite(temp)) {
                    series.add(time, temp);
                }
            }
            dataset.addSeries(series); // データセットに系列を追加

            //グラフの更新
            JFreeChart chart = ChartFactory.createXYLineChart(
                beanName + " 焙煎プロファイル", // グラフタイトルを豆の名前に
                "時間 (分)",            // X軸ラベル
                "温度 (℃)",             // Y軸ラベル
                dataset                  // 新しいデータセット
            );

            // グラフの背景色を透明に設定
            chart.setBackgroundPaint(new Color(0, 0, 0, 0)); // 透明

            // プロットの背景色を透明に設定
            XYPlot plot = chart.getXYPlot();
            plot.setBackgroundPaint(new Color(0, 0, 0, 0)); // 透明

            //凡例を非表示にする
            if (chart.getLegend() != null) { // 凡例が存在する場合のみ
                chart.getLegend().setVisible(false);
            }

            //フォント設定
            Font japaneseFont = new Font("SansSerif", Font.PLAIN, 12); // 日本語対応のフォント
            if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                // Windowsでより適切なフォントを試す。システムにMeiryo UIがなければSansSerifに戻る
                GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
                String[] fontNames = ge.getAvailableFontFamilyNames();
                boolean meiryoExists = false;
                for (String fontName : fontNames) {
                    if (fontName.equals("Meiryo UI")) {
                        meiryoExists = true;
                        break;
                    }
                }
                if (meiryoExists) {
                    japaneseFont = new Font("Meiryo UI", Font.PLAIN, 12);
                } else {
                    japaneseFont = new Font("Dialog", Font.PLAIN, 12);
                }
            } else if (System.getProperty("os.name").toLowerCase().contains("mac")) {
                // Macでより適切なフォントを試す
                GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
                String[] fontNames = ge.getAvailableFontFamilyNames();
                boolean hiraginoExists = false;
                for (String fontName : fontNames) {
                    if (fontName.equals("Hiragino Sans")) {
                        hiraginoExists = true;
                        break;
                    }
                }
                if (hiraginoExists) {
                    japaneseFont = new Font("Hiragino Sans", Font.PLAIN, 12);
                } else {
                    japaneseFont = new Font("Dialog", Font.PLAIN, 12); // Fallback
                }
            } else {
                japaneseFont = new Font("Dialog", Font.PLAIN, 12); // Linuxなどの場合
            }


            //グラフの表示
            chart.getTitle().setFont(japaneseFont.deriveFont(Font.BOLD, 14)); // タイトル
            plot.getDomainAxis().setLabelFont(japaneseFont); // X軸ラベル
            plot.getRangeAxis().setLabelFont(japaneseFont);  // Y軸ラベル
            plot.getDomainAxis().setTickLabelFont(japaneseFont); // X軸目盛りラベル
            plot.getRangeAxis().setTickLabelFont(japaneseFont); // Y軸目盛りラベル
            
            // Y軸の範囲を自動調整しつつ、少し余裕を持たせる
            NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
            rangeAxis.setAutoRangeIncludesZero(false); // 0を含めるかどうか
            rangeAxis.setAutoRange(true); // 自動範囲調整を有効にする
            rangeAxis.setStandardTickUnits(NumberAxis.createStandardTickUnits());

            // 線と点の表示設定 
            XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
            renderer.setSeriesLinesVisible(0, true); // 線を表示
            renderer.setSeriesShapesVisible(0, true); // 点を表示
            plot.setRenderer(renderer);


            // 既存のChartPanelに新しいグラフをセット
            chartPanel.setChart(chart);

        } catch (SQLException e) {//その他のエラー
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "詳細表示に失敗しました。" + e.getMessage(), "エラー", JOptionPane.ERROR_MESSAGE);
        }
    }

    //焙煎表のデータ管理
    class RoastPanel extends JPanel {
        private JTable table;
        private RoastTableModel tableModel;
        private JButton saveButton, backButton;
        private Connection conn;
        private BeanApp_test parentApp; // BeanApp_testインスタンスへの参照

        //焙煎表の枠組み
        public RoastPanel(Connection conn, CardLayout cardLayout, JPanel mainPanel, BeanApp_test parentApp) {
            this.conn = conn;
            this.parentApp = parentApp;
            setLayout(new BorderLayout());

            tableModel = new RoastTableModel(parentApp); // 親アプリの参照をTableModelに渡す
            table = new JTable(tableModel);

            // スクロール可能にする
            table.setRowHeight(25);
            JScrollPane scrollPane = new JScrollPane(table);
            add(scrollPane, BorderLayout.CENTER);

            // ボタン設定
            saveButton = new JButton("保存");
            backButton = new JButton("戻る");

            //ボタンを下部に追加
            JPanel buttonPanel = new JPanel();
            buttonPanel.add(saveButton);
            buttonPanel.add(backButton);
            add(buttonPanel, BorderLayout.SOUTH);

            // 保存処理。保存したら豆の詳細画面に戻る
            saveButton.addActionListener(e -> saveRoastData());
            saveButton.addActionListener(e -> cardLayout.show(mainPanel, "Create"));


            // 豆の詳細画面に戻る処理
            backButton.addActionListener(e -> cardLayout.show(mainPanel, "Create"));

            // このパネルが表示されるたびに、現在の豆の焙煎データをロードするリスナーを追加
            this.addComponentListener(new ComponentAdapter() {
                @Override
                public void componentShown(ComponentEvent e) {
                    String beanName = parentApp.getCurrentSelectedBeanNameForRoast();
                    loadRoastDataForBean(beanName); // 豆の名前を渡してデータをロード
                    // calculateAndSetBottomはloadRoastDataForBean内で呼ばれる
                }
            });
        }

        // 特定の豆に関連付けられた焙煎データをロードする
        private void loadRoastDataForBean(String beanName) {
            tableModel.setRowCount(0); // まずテーブルを完全にクリア (既存データ読み込み用)

            try {
                // データベースから焙煎データをロード
                PreparedStatement ps = conn.prepareStatement("SELECT time_minute, temperature, temp_change, gas_pressure, damper, note FROM roast_table WHERE bean_name = ? ORDER BY time_minute");
                ps.setString(1, beanName);
                ResultSet rs = ps.executeQuery();

                // DBから読み込んだデータを一時的に保持し、後で正しい順序でaddRowする
                java.util.List<Object[]> loadedData = new java.util.ArrayList<>();
                boolean dataFound = false;
                while (rs.next()) {
                    dataFound = true;
                    int time = rs.getInt("time_minute");
                    double temp = rs.getDouble("temperature");
                    double tempChange = rs.getDouble("temp_change");
                    String gasPressure = rs.getString("gas_pressure");
                    String damper = rs.getString("damper");
                    String note = rs.getString("note");
                    loadedData.add(new Object[]{time, temp, tempChange, gasPressure, damper, note});
                }

                // 0分から30分までの行を生成し、ロードされたデータを埋め込む
                // まず空の31行を作成
                for (int i = 0; i <= 30; i++) {
                    Object[] rowData = new Object[tableModel.getColumnCount()];
                    rowData[0] = i; // 時間の列に値を設定
                    tableModel.addRow(rowData); // addRowはsetValueAtを呼ばない
                }

                // ロードされたデータを対応する時間に埋め込む
                for (Object[] loadedRow : loadedData) {
                    int time = (Integer) loadedRow[0];
                    if (time >= 0 && time <= 30 && time < tableModel.getRowCount()) {
                        // setValueAtを呼び出し、温度変化の自動計算をトリガーする
                        tableModel.setValueAt(loadedRow[1], time, 1); // 温度
                        tableModel.setValueAt(loadedRow[3], time, 3); // ガス圧
                        tableModel.setValueAt(loadedRow[4], time, 4); // ダンパー
                        tableModel.setValueAt(loadedRow[5], time, 5); // メモ
                    }
                }
                
                // すべてのデータがセットされた後、念のため温度変化を再計算
                tableModel.recalculateAllTempChanges();
                // ボトムも計算し設定
                parentApp.calculateAndSetBottom();

            } catch (SQLException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this, "焙煎データの読み込み中にエラーが発生しました。入力は可能です。", "情報", JOptionPane.INFORMATION_MESSAGE);
                // エラーが発生した場合でも、テーブルを空の初期行で埋める
                tableModel.setRowCount(0); // 念のためクリア
                for (int i = 0; i <= 30; i++) { // 0から30までの31行を生成
                    Object[] rowData = new Object[tableModel.getColumnCount()];
                    rowData[0] = i;
                    tableModel.addRow(rowData);
                }
                tableModel.recalculateAllTempChanges(); // エラー時も計算
                parentApp.calculateAndSetBottom(); // エラー時もボトムを計算
            }
        }


        private void saveRoastData() {
            String beanName = parentApp.getCurrentSelectedBeanNameForRoast();
            if (beanName == null || beanName.isEmpty()) {
                JOptionPane.showMessageDialog(this, "焙煎データを保存する豆が選択されていません。先に豆情報を入力・保存してください。", "エラー", JOptionPane.ERROR_MESSAGE);
                return;
            }

            try {
                // beanExists()で豆情報がDBに存在することを前提とする
                // 既存の焙煎データを削除 
                PreparedStatement deletePs = conn.prepareStatement("DELETE FROM roast_table WHERE bean_name = ?");
                deletePs.setString(1, beanName);
                deletePs.executeUpdate();

                // 新しい焙煎データを挿入
                PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO roast_table (bean_name, time_minute, temperature, temp_change, gas_pressure, damper, note) VALUES (?, ?, ?, ?, ?, ?, ?)"
                );
                boolean hasDataToSave = false;
                for (int i = 0; i < tableModel.getRowCount(); i++) {
                    // 時間(分)が入力されている行のみを保存対象とする
                    Object timeValue = tableModel.getValueAt(i, 0);
                    // 時間が0の場合も保存対象に含める
                    if (timeValue != null && (timeValue instanceof Integer) && (Integer)timeValue >= 0 && !timeValue.toString().trim().isEmpty()) { // 時間が有効な整数であることを確認
                        try {
                            ps.setString(1, beanName);
                            ps.setInt(2, (Integer) timeValue);
                            // parseDoubleSafelyは空文字列を0.0に変換するため、問題なし
                            // 値がNaNならSQLにはNULLとして保存
                            Double tempForDB = tableModel.parseDoubleSafely(tableModel.getValueAt(i, 1));
                            if (Double.isNaN(tempForDB)) {
                                ps.setNull(3, Types.DOUBLE);
                            } else {
                                ps.setDouble(3, tempForDB);
                            }
                            
                            Double tempChangeForDB = tableModel.parseDoubleSafely(tableModel.getValueAt(i, 2));
                            if (Double.isNaN(tempChangeForDB)) {
                                ps.setNull(4, Types.DOUBLE);
                            } else {
                                ps.setDouble(4, tempChangeForDB);
                            }

                            ps.setString(5, (String) tableModel.getValueAt(i, 3));
                            ps.setString(6, (String) tableModel.getValueAt(i, 4));
                            ps.setString(7, (String) tableModel.getValueAt(i, 5));
                            ps.addBatch();
                            hasDataToSave = true;
                        } catch (NumberFormatException nfe) {
                            JOptionPane.showMessageDialog(this, (i + 1) + "行目の数値が不正です。確認してください。", "入力エラー", JOptionPane.ERROR_MESSAGE);
                            return; // エラーがあれば保存処理を中断
                        }
                    }
                }
                if (hasDataToSave) {
                    ps.executeBatch();
                    JOptionPane.showMessageDialog(this, "焙煎データが保存されました！");
                } else {
                    JOptionPane.showMessageDialog(this, "保存する焙煎データがありません。", "情報", JOptionPane.INFORMATION_MESSAGE);
                }

            } catch (SQLException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this, "焙煎データの保存に失敗しました。",
                                                "エラー", JOptionPane.ERROR_MESSAGE);
            }
        }

        private double parseDoubleSafely(Object value) {
            try {
                // RoastTableModelのparseDoubleSafelyを使う
                return tableModel.parseDoubleSafely(value);
            } catch (NumberFormatException e) {
                return 0.0; // 数値変換失敗時は0.0を返す
            }
        }
    }

    //焙煎表の値取得、変更を書き換えるメソッド
    class RoastTableModel extends AbstractTableModel {
        private String[] columnNames = { "時間(分)", "温度", "温度変化", "ガス圧", "ダンパー", "メモ" };
        private java.util.List<Object[]> data; // Listに変更して動的に行数を管理
        private BeanApp_test parentApp; // 親アプリの参照

        public RoastTableModel(BeanApp_test parentApp) { // 親アプリの参照をコンストラクタで受け取る
            this.parentApp = parentApp;
            data = new java.util.ArrayList<>(); // ArrayListで初期化
            // 初期データはloadRoastDataForBeanで生成されるため、ここでは何もしない
        }

        //行の総数について
        @Override
        public int getRowCount() {
            return data.size(); // リストのサイズがそのまま行数
        }

        //列の総数について
        @Override
        public int getColumnCount() {
            return columnNames.length;
        }

        //各列のヘッダー名取得
        @Override
        public String getColumnName(int column) {
            return columnNames[column];
        }

        //セルの値取得
        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            if (rowIndex >= data.size() || columnIndex >= data.get(rowIndex).length) {
                return null; // インデックスが範囲外の場合
            }
            return data.get(rowIndex)[columnIndex];
        }

        //数値が変更された際にそれを反映する
        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            if (rowIndex >= data.size()) {
                 // addRowと同様のロジックで新しい行を追加
                 Object[] newRow = new Object[columnNames.length];
                 newRow[0] = rowIndex; // 時間
                 // 他の列はnull
                 data.add(newRow);
                 fireTableRowsInserted(rowIndex, rowIndex);
            }

            data.get(rowIndex)[columnIndex] = aValue; // まず値を設定

            // 温度が変更されたら温度変化を自動計算し、ボトムも計算する
            if (columnIndex == 1) { // 温度の列が編集された場合
                if (rowIndex > 0) { // 最初の行以外の場合のみ温度変化を計算
                    Double currentTemp = parseDoubleSafely(aValue);
                    Double prevTemp = parseDoubleSafely(getValueAt(rowIndex - 1, 1));

                    if (!currentTemp.isNaN() && !prevTemp.isNaN()) { // NaNでないことを確認
                        double tempChange = currentTemp - prevTemp;
                        data.get(rowIndex)[2] = String.format("%.2f", tempChange); // 温度変化の列に設定（インデックス2）
                    } else {
                        data.get(rowIndex)[2] = null; // 数値が不正なら温度変化もクリア
                    }
                    fireTableCellUpdated(rowIndex, 2); // 温度変化のセルが更新されたことを通知
                } else { // 最初の行の場合、温度変化はなし
                    data.get(rowIndex)[2] = null; // 温度変化をクリア
                    fireTableCellUpdated(rowIndex, 2); // 温度変化のセルが更新されたことを通知
                }
                // 温度が変更されたら、ボトムも再計算して設定
                parentApp.calculateAndSetBottom();
            }
            fireTableCellUpdated(rowIndex, columnIndex); // 編集された元のセルを更新
        }
        
        // テーブル全体の温度変化を再計算するヘルパーメソッド (TableModel内部で完結するように変更)
        public void recalculateAllTempChanges() {
            for (int i = 0; i < getRowCount(); i++) {
                if (getColumnCount() > 2) { // 温度変化カラムが存在することを確認
                    if (i > 0) { // 最初の行以外の場合のみ温度変化を計算
                        Double currentTemp = parseDoubleSafely(getValueAt(i, 1));
                        Double prevTemp = parseDoubleSafely(getValueAt(i - 1, 1));

                        if (!currentTemp.isNaN() && !prevTemp.isNaN()) {
                            double tempChange = currentTemp - prevTemp;
                            // 直接data配列を更新し、fireTableCellUpdatedで通知
                            data.get(i)[2] = String.format("%.2f", tempChange);
                            fireTableCellUpdated(i, 2);
                        } else {
                            data.get(i)[2] = null;
                            fireTableCellUpdated(i, 2);
                        }
                    } else { // 最初の行の場合
                        data.get(i)[2] = null;
                        fireTableCellUpdated(i, 2);
                    }
                }
            }
        }


        //編集可能かどうかの判定
        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            // 温度変化は自動計算なので編集不可にする
            if (columnIndex == 2) {
                return false; 
            }
            // 時間の列も固定なので編集不可にする
            if (columnIndex == 0) {
                return false;
            }
            return true; // その他の列は編集可能
        }

        //編集する列の型を伝達
        @Override
        public Class<?> getColumnClass(int columnIndex) {
            // 整数型はInteger.class、浮動小数点型はDouble.class
            if (columnIndex == 0) return Integer.class; // 時間(分)
            if (columnIndex == 1 || columnIndex == 2) return Double.class; // 温度、温度変化
            return String.class; // その他は文字列
        }

        // parseDoubleSafelyをRoastTableModel内でも定義（setValueAtで使用するため）
        // publicにしてRoastPanelからもアクセスできるようにする
        public double parseDoubleSafely(Object value) {
            try {
                return value != null && !value.toString().trim().isEmpty() ? Double.parseDouble(value.toString()) : Double.NaN; // 数値変換失敗時はNaNを返す
            } catch (NumberFormatException e) {
                return Double.NaN; // 数値変換失敗時はNaNを返す
            }
        }

        // テーブルの行数を設定する（データロード時に使用）
        public void setRowCount(int rowCount) {
            data.clear(); // データをクリア
            fireTableDataChanged(); // データ全体が変更されたことをJTableに通知
        }

        // 新しい行を追加する
        public void addRow(Object[] rowData) {
            data.add(rowData);
            fireTableRowsInserted(data.size() - 1, data.size() - 1); // 行が追加されたことをJTableに通知
        }
    }

      //メイン関数
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new BeanApp_test().setVisible(true));
    }

}