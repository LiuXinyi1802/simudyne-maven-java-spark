package tokyo;

import simudyne.core.abm.AgentBasedModel;
import simudyne.core.abm.GlobalState;
import simudyne.core.abm.Group;
import simudyne.core.abm.stats.AgentStatisticsResult;
import simudyne.core.annotations.Constant;
import simudyne.core.annotations.Variable;
import simudyne.core.data.CSVSource;
import simudyne.core.abm.Agent;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;


public class TokyoModel extends AgentBasedModel<TokyoModel.Globals> {
    private int length = 0;
    @Constant
    public int nbLargeBanks =5;

    @Constant
    public int nbSmallBanks = 100;

    @Constant
    public int networkType =0; // 0: Scale-Free, 1: core-periphery Network, 2: Random

    @Constant
    public int weightType = 1; // 0: Uniform, 1: Gaussian



    public static class Globals extends GlobalState {
        public boolean LargeBankrupty = false;

        @Variable
        public double riskAssetPrice = 10.0;

        public double riskAssetPrice_1=10.0;//cal char term time is t

        public double riskAssetPrice_2;// cal char term time is t-1

        @Variable
        public int nbRiskAssetsBought = 0; // number of risk assets bought in previous period

        @Variable
        public int nbRiskAssetsSold = 0; // risk assets sold in previous period

        @Variable
        public int numB = 0, check = 0;

        @Constant
        public double alpha = 0.5; // coefficient of price fluctuations

        @Constant
        public int totRiskAssets = 100; // Total risk assets

        @Variable
        public double logicalPrice = 10.0;

        public double logicalPrice_1;

        @Constant
        public double mu = 0.0;

        @Constant
        public double sigma = 0.01;//add public

        //@Variable
        public double taoMeanLB;

        //@Variable
        public double taoMeanSB;

        @Constant
        public double sigmaE = 1.0;
    }

    @Override
    public void init() {
        registerAgentTypes(LargeBank.class, SmallBank.class);
        registerLinkTypes(Links.selfLink.class, Links.largeToSmall.class, Links.smallToLarge.class);
        registerMessageTypes();
        createLongAccumulator("nbDefaults");
    }

    @Override
    public void setup() {

        // Scale-Free
        if (networkType == 0) {
            CSVSource linkSource = new CSVSource("ScaleFree.csv");

            Group largeBankGroupSF = generateGroup(LargeBank.class, nbLargeBanks);
            Group smallBankGroupSF = generateGroup(SmallBank.class, nbSmallBanks);

            largeBankGroupSF.partitionConnected(smallBankGroupSF,Links.largeToSmall.class);
            smallBankGroupSF.partitionConnected(largeBankGroupSF,Links.smallToLarge.class);
            //smallBankGroupSF.fullyConnected(smallBankGroupSF,Links.selfLink.class);
            smallBankGroupSF.loadConnections(smallBankGroupSF, Links.selfLink.class, linkSource);


        }

        // core-periphery Network
        else if (networkType == 1) {

            MonteCarlo();
            CSVSource linkSource = new CSVSource("random_network.csv");

            Group<LargeBank> largeBankGroupSF = generateGroup(LargeBank.class, nbLargeBanks);
            Group<SmallBank> smallBankGroupSF = generateGroup(SmallBank.class, nbSmallBanks);

            largeBankGroupSF.fullyConnected(smallBankGroupSF,Links.largeToSmall.class);
            smallBankGroupSF.fullyConnected(largeBankGroupSF,Links.smallToLarge.class);
            largeBankGroupSF.fullyConnected(largeBankGroupSF, Links.selfLink.class);
            smallBankGroupSF.loadConnections(smallBankGroupSF, Links.selfLink.class, linkSource);

        }

        // Random Network (Erdős-Rényi graph)
        else if (networkType == 2) {
            CSVSource linkSource = new CSVSource("random_network.csv");
            Group<SmallBank> smallBankGroupRand = generateGroup(SmallBank.class, nbSmallBanks);
            smallBankGroupRand.loadConnections(smallBankGroupRand, Links.selfLink.class, linkSource);

        } else {
            throw new RuntimeException("Network Type should take a value of 0, 1 or 2. Try again. ");
        }
        super.setup();
    }


    @Override
    public void step() {
        super.step();
        length++;

        // Some initialisation at the first tick
        if (getContext().getTick() == 0) {
            if (weightType == 0) {
                run(SmallBank.generateWeightsFromUniform());
                run(LargeBank.generateWeightsFromUniform());
            }
            else {
                run(SmallBank.generateWeightsFromGaussian());
                run(LargeBank.generateWeightsFromGaussian());
            }
        } else {
            if(length<=24) {  //without systemic shocks
                updatePrices();
                run(tokyo.LargeBank.computeFundamentalTerm());
                run(tokyo.LargeBank.computeChartTerm());
                run(tokyo.LargeBank.computeNoiseTerm());
                run(tokyo.LargeBank.computeVaR());
                run(tokyo.LargeBank.buyOrSell());
                run(tokyo.LargeBank.updateCapitalAdequacy());
                run(tokyo.LargeBank.checkCapitalAdequacy());

                System.out.println("len"+length);
                run(tokyo.SmallBank.computeFundamentalTerm());
                run(tokyo.SmallBank.computeChartTerm());
                run(tokyo.SmallBank.computeNoiseTerm());
                run(tokyo.SmallBank.computeVaR());
                run(tokyo.SmallBank.buyOrSell());
                run(tokyo.SmallBank.updateCapitalAdequacy());
                run(tokyo.SmallBank.checkCapitalAdequacy());
                setLaggedPrices();
            }else {    //systemic shocks occur after two years
                System.out.println("len"+length);
                getGlobals().LargeBankrupty = true;
                updatePrices();
                run(tokyo.SmallBank.computeFundamentalTerm());
                run(tokyo.SmallBank.computeChartTerm());
                run(tokyo.SmallBank.computeNoiseTerm());
                run(tokyo.SmallBank.computeVaR());
                run(tokyo.SmallBank.buyOrSell());
                run(tokyo.SmallBank.updateCapitalAdequacy());
                run(tokyo.SmallBank.checkCapitalAdequacy());
                setLaggedPrices();
            }
        }
    }

    public static void MonteCarlo (){
        int numNodes = 100; // Number of nodes in the network
        double edgeProbability = 0.10; // Probability of a connection between nodes

        List<List<Integer>> randomNetwork = generateRandomNetwork(numNodes, edgeProbability);

        // Write the random network connections to a CSV file
        writeNetworkToCSV(randomNetwork, "random_network.csv");
//        addLink(randomNetwork,smallbank);
    }

    public static List<List<Integer>> generateRandomNetwork(int numNodes, double edgeProbability) {
        List<List<Integer>> randomNetwork = new ArrayList<>();

        Random random = new Random();

        // Generate connections for each node
        for (int i = 1; i <= numNodes; i++) {
            List<Integer> connections = new ArrayList<>();

            // Iterate over all other nodes and decide if they should be connected
            for (int j = 1; j <=numNodes; j++) {
                if (i != j && random.nextDouble() < edgeProbability) {
                    connections.add(j);
                }
            }

            randomNetwork.add(connections);
        }

        return randomNetwork;
    }


    public static void writeNetworkToCSV(List<List<Integer>> randomNetwork, String filename) {

        try (FileWriter writer = new FileWriter(filename)) {
            StringBuilder line = new StringBuilder();
            line.append("from,to").append("\n");
            writer.write(line.toString());
            for (int i = 1; i <=randomNetwork.size(); i++) {
                StringBuilder line1 = new StringBuilder();
                for (int neighbor : randomNetwork.get(i-1)) {
                    line1.append(i).append(",").append(neighbor).append("\n");

                }
                writer.write(line1.toString());

            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void updatePrices() {
        AgentStatisticsResult<LargeBank> taoStatsLB =
                select(LargeBank.class).stats().field("tao", b -> b.tao).get();
        getGlobals().taoMeanLB = taoStatsLB.getField("tao").getMean();

        AgentStatisticsResult<SmallBank> taoStatsSB =
                select(SmallBank.class).stats().field("tao", b -> b.tao).get();
        getGlobals().taoMeanSB = taoStatsSB.getField("tao").getMean();

        // Update risk asset price
        getGlobals().riskAssetPrice =
                getGlobals().riskAssetPrice_1
                        + getGlobals().alpha * getGlobals().riskAssetPrice_1
                        * (getGlobals().nbRiskAssetsBought - getGlobals().nbRiskAssetsSold)
                        / getGlobals().totRiskAssets; // eqn 2

        // Update logical price
        getGlobals().logicalPrice =
                getGlobals().logicalPrice
                        + (getGlobals().mu * getGlobals().logicalPrice)
                        + (getGlobals().sigma * getGlobals().logicalPrice
                        * getContext().getPrng().gaussian(0.0, 1.0).sample());

        getGlobals().nbRiskAssetsBought = 0;
        getGlobals().nbRiskAssetsSold = 0;

    }

    public void setLaggedPrices() {
        getGlobals().riskAssetPrice_2 = getGlobals().riskAssetPrice_1; //time = t-1
        getGlobals().riskAssetPrice_1 = getGlobals().riskAssetPrice; //time = t
        getGlobals().logicalPrice_1 = getGlobals().logicalPrice;
    }
}