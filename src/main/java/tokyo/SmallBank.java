package tokyo;

import simudyne.core.abm.Action;
import simudyne.core.abm.Agent;
import simudyne.core.annotations.Variable;
import tokyo.Links;


public class SmallBank extends Agent<TokyoModel.Globals> {

    private static double z95 = 1.645; //z-score for 95% normal distribution
    public static int check,  num_n =0;

   // @Variable
   // public static int numB = 0;

    //@Variable
    public double fundamentalTerm;

    public double chartTerm;

    public double noiseTerm;

    public double fundamentalWeight;

    public double chartWeight;

    public double noiseWeight;

    public double tao = 1.0;

    @Variable
    public double capAdequacy;
    @Variable
    public double netWorth;
    @Variable
    public double valueAtRisk;

    public double stockNum = 1500;

    public boolean bankrupt = false;

    public double expReturn;


    public double expPrice;
    @Variable
    public double netLending;

    public static int taoi = 3;

    public static Action<SmallBank> generateWeightsFromUniform() {
        return Action.create(SmallBank.class, smallBank -> {

            smallBank.fundamentalWeight = smallBank.getPrng().uniform(0.0, 1.0).sample();
            smallBank.chartWeight = smallBank.getPrng().uniform(0.0, 1.0).sample();
            smallBank.noiseWeight = smallBank.getPrng().uniform(0.0, 1.0).sample();

            double sum = smallBank.fundamentalWeight + smallBank.chartWeight + smallBank.noiseWeight;

            smallBank.fundamentalWeight = smallBank.fundamentalWeight * (1.0 / sum);
            smallBank.chartWeight = smallBank.chartWeight * (1.0 / sum);
            smallBank.noiseWeight = smallBank.noiseWeight * (1.0 / sum);
        });
    }

    public static Action<SmallBank> generateWeightsFromGaussian() {
        return Action.create(SmallBank.class, smallBank -> {

            smallBank.fundamentalWeight = smallBank.getPrng().gaussian(5.0, 1.0).sample();
            smallBank.chartWeight = smallBank.getPrng().gaussian(5.0, 1.0).sample();
            smallBank.noiseWeight = smallBank.getPrng().gaussian(5.0, 1.0).sample();

            double sum = smallBank.fundamentalWeight + smallBank.chartWeight + smallBank.noiseWeight;

            smallBank.fundamentalWeight = smallBank.fundamentalWeight * (1.0 / sum);
            smallBank.chartWeight = smallBank.chartWeight * (1.0 / sum);
            smallBank.noiseWeight = smallBank.noiseWeight * (1.0 / sum);


        });
    }


    public static Action<SmallBank> computeFundamentalTerm() {
        return Action.create(SmallBank.class, smallBank -> {
            smallBank.fundamentalTerm = (1 / smallBank.getGlobals().taoMeanSB) * Math.log(
                    smallBank.getGlobals().logicalPrice
                            / smallBank.getGlobals().riskAssetPrice); // eqn 7
        });
    }


    public static Action<SmallBank> computeChartTerm() {
        return Action.create(SmallBank.class, smallBank -> {
            smallBank.chartTerm =
                    1 / smallBank.tao * Math.log(smallBank.getGlobals().riskAssetPrice_1
                            / smallBank.getGlobals().riskAssetPrice_2);

        });
    }

    public static Action<SmallBank> computeNoiseTerm() {
        return Action.create(SmallBank.class, smallBank -> {
            smallBank.noiseTerm =
                    smallBank.getPrng().gaussian(0.0, smallBank.getGlobals().sigmaE).sample();
        });
    }

    public static Action<SmallBank> computeVaR() {
        return Action.create(SmallBank.class, smallBank -> {
            smallBank.valueAtRisk =
                smallBank.getGlobals().sigma * (smallBank.stockNum * smallBank.getGlobals().riskAssetPrice) * z95;

          });
    }

    public static Action<SmallBank> buyOrSell() {
        return Action.create(SmallBank.class, smallBank -> {
            if (!smallBank.bankrupt) {
                smallBank.expReturn =
                        1 / (smallBank.fundamentalWeight + smallBank.chartWeight + smallBank.noiseWeight)
                                * (smallBank.fundamentalWeight * smallBank.fundamentalTerm
                                + smallBank.noiseWeight * smallBank.noiseTerm
                                + smallBank.chartWeight * smallBank.chartTerm); // eqn 5

                smallBank.expPrice =
                        smallBank.getGlobals().riskAssetPrice
                                * Math.exp(smallBank.expReturn * smallBank.tao);

                if (smallBank.expPrice >= smallBank.getGlobals().riskAssetPrice) {
                    // Buy a stock
                    smallBank.getGlobals().nbRiskAssetsBought += 1;
                    smallBank.stockNum += 1;
                } else {
                    // Sell a stock
                    smallBank.getGlobals().nbRiskAssetsSold += 1;
                    smallBank.stockNum -= 1;
                }
            }
        });
    }

    public static Action<SmallBank> updateCapitalAdequacy() {

        return Action.create(SmallBank.class, smallBank -> {
            //System.out.println(smallBank.getGlobals().LargeBankrupty);
            if (!smallBank.getGlobals().LargeBankrupty) {

                // Make the amount of interbank lending count towards the Net Worth of the bank.
                smallBank.netLending = (smallBank.getLinks().size()-5) * 10 -80; // each loan from smallBank is for 10, from each largeBnak is 80
               System.out.println(smallBank.getLinks()+" size "+smallBank);
               System.out.println(" "+smallBank.netLending);
                smallBank.netWorth = (smallBank.stockNum * smallBank.getGlobals().riskAssetPrice)
                        - smallBank.netLending; // added as omitted
                 smallBank.capAdequacy = smallBank.netWorth / (smallBank.valueAtRisk * smallBank.stockNum);//
            }
            else {

                smallBank.netLending = (smallBank.getLinks().size()-5) * 10; // each loan from smallBank is for 10
                System.out.println(smallBank.getLinks()+" size "+smallBank);
                System.out.println(smallBank.getLinks().size()+" "+smallBank.netLending);
                 smallBank.netWorth = (smallBank.stockNum * smallBank.getGlobals().riskAssetPrice)
                        - smallBank.netLending; // added as omitted
               smallBank.capAdequacy = smallBank.netWorth / (smallBank.valueAtRisk * smallBank.stockNum);//
            }
        });
    }

    public static Action<SmallBank> checkCapitalAdequacy() {
        check=0;

        return Action.create(SmallBank.class, smallBank -> {
                if (smallBank.capAdequacy < 0.04) {
                    check+=1;
                    System.out.println("CAQ "+smallBank);
                    smallBank.getGlobals().numB++;
                    smallBank.getGlobals().check=check;
                    smallBank.capAdequacy = 0.04;
                    smallBank.bankrupt = true; //
                    smallBank.getLongAccumulator("nbDefaults").add(1);
                    smallBank.removeLinks();

                }
        });

    }


}
