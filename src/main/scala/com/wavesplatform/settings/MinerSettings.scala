package com.wavesplatform.settings

import com.wavesplatform.mining.Miner

import scala.concurrent.duration.FiniteDuration

case class MinerSettings(
    enable: Boolean,
    quorum: Int,
    intervalAfterLastBlockThenGenerationIsAllowed: FiniteDuration,
    microBlockInterval: FiniteDuration,
    minimalBlockGenerationOffset: FiniteDuration,
    maxTransactionsInKeyBlock: Int,
    maxTransactionsInMicroBlock: Int,
    minMicroBlockAge: FiniteDuration) {
  require(maxTransactionsInMicroBlock < Miner.MaxTransactionsPerMicroblock)
}
