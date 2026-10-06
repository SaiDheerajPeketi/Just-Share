"""One-use Just-Share permission recovery debug APK-pair packaging; preparation grants no execution authority."""
from pathlib import Path
import argparse
import hashlib
import importlib.util
import json
import os
import re
import shutil
import stat
import subprocess
import sys
import time
import traceback
import io
import zipfile

REPO = Path(__file__).resolve().parents[1]
PACKET = REPO / "docs/permission-recovery-debug-packaging-20261006-one"
CLAIM = REPO / "docs/permission-recovery-debug-packaging-20261006-one.claim.json"
SELF = "docs/run-permission-recovery-debug-packaging-20261006.py"
APP_SOURCE = 'd66c25bf172fc821052298f34a6936bbf3b925a3'
SOURCE_ACCEPTED = True
HELPER = Path("/Users/speketi/Projects/HearthLedger/tools/run_offline_release.py")
HELPER_SHA = "b7667e7646e9e0a6d650fecdfdc8a4563f5a1153d068125c781744684283202e"
HELPER_INPUTS = HELPER.with_name("release_input_fingerprints.py")
HELPER_INPUTS_SHA = "60452b0711b86e51f836f6a2d9365bf28a04bcce5318d39a54d8ee9c9182fb7a"
JAVA_HOME = Path("/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home")
TREES = ["app/src"]
REVIEWED_SOURCE_TREES = {'app/src': '5b78e7caa7244e6aa7795e118dbd835f2b34cae3'}
REVIEWED_SOURCE_SHA256 = {'app/src/androidTest/java/com/blackandblue/justshare/ExampleInstrumentedTest.kt': 'ebc1abf5b1227a3f08587aa9b1cc33986b49090de3f6783146eb31e2039f6d16',
 'app/src/androidTest/java/com/blackandblue/justshare/TransferConnectionOwnershipTest.kt': '736bb1898eee2e5cf50a67d758ad7f25853476604394547600254f9366c2ef4f',
 'app/src/androidTest/java/com/blackandblue/justshare/data/chat/BluetoothDiscoveryPermissionTest.kt': '5539c01e236fbc52fc053e3c5696682ec6a59373aec0bd4eb236167abd4413c7',
 'app/src/androidTest/java/com/blackandblue/justshare/navigation/LocalTransportOwnershipTest.kt': '7e9b54891e7e076f5679abc5d7533ca947a82b2a87a34a5146d5c5f4350eddca',
 'app/src/androidTest/java/com/blackandblue/justshare/navigation/PermissionRecoveryNavigationTest.kt': 'b510b712899c6be250f4ab3e7a8caad9ad73929c425550621b2e5c476252a5e4',
 'app/src/androidTest/java/com/blackandblue/justshare/presentation/TransferRetryStateTest.kt': '1c68ea0a48b4a59c20bfd4a2e024623de58bf6aa45679211611d0cc0089f7247',
 'app/src/androidTest/java/com/blackandblue/justshare/presentation/WifiPermissionRecoveryTest.kt': '9ff16ad59663a4e945c50c94496b3d9cf9c34313ecea0359398b3e8ab13ac69c',
 'app/src/androidTest/java/com/blackandblue/justshare/ui/components/TransferExitGuardTest.kt': 'a40b6bfcf29408dc08513bd6a2c6247f1bfc614a479ae44df7d60162b79df961',
 'app/src/androidTest/java/com/blackandblue/justshare/ui/screens/PermissionsScreenTest.kt': '79164d4e8939f92e859f15c90128d2150477c5b107f6ce5e00e2d1bd809b7c4c',
 'app/src/debug/java/com/blackandblue/justshare/AppCheckInstaller.kt': '6bd981b7c9cf56f8a28ae6c93e8f73b1c4d635e72e44556765c6b1da5d64570e',
 'app/src/main/AndroidManifest.xml': '11c335230084cfbf9e482edceda02219827e4e035f8182a0b720b6f567c19c51',
 'app/src/main/ic_launcher-playstore.png': '0ad00a0059e98313cf01476fcb6bdb3c8abe27560b7284adc9cb54377019fc5c',
 'app/src/main/java/com/blackandblue/justshare/AlterSendForegroundService.kt': '5653f1e08f57b61efd353acf93619b4dd7ec5bf25a235bfdfc40107c12863c1d',
 'app/src/main/java/com/blackandblue/justshare/CommunicationService.kt': '92b4df95b4377b848618d40d041e06e5544607e59293def6abd1fcc89232c6e2',
 'app/src/main/java/com/blackandblue/justshare/JustShareTelemetry.kt': '303af11cd47705ec5b3a27a264c8bb677c173ced9d816503c7e641dcb2838a51',
 'app/src/main/java/com/blackandblue/justshare/LocalTransferPermissions.kt': '4b23ae1aad33b5eb74d08bd5651af524aaf40dff30697366cf9276d609085757',
 'app/src/main/java/com/blackandblue/justshare/MainActivity.kt': '44a2389f1e280147f8d09854f043285d71b1570141990047c01d94e25cd7e765',
 'app/src/main/java/com/blackandblue/justshare/PermissionDialog.kt': '2ed6f0c10504775b83bdfdfc7f586b7128d0d6ce04ac4cb2855fe1bbc48efe14',
 'app/src/main/java/com/blackandblue/justshare/SharingApp.kt': '18f6d0a883490ec1efca66b78dce6c5e1055076af91aa03226962f8936e2e506',
 'app/src/main/java/com/blackandblue/justshare/Utils.kt': '60380ed60bd5376d7327bf0b116f52148bafc4d37d297fbc223edeee984edba5',
 'app/src/main/java/com/blackandblue/justshare/WiFiDirectBroadcastReceiver.kt': 'b900335c7c330d978c98d4872f6482c3bd3efdc6d5d680b452dfaaebef1137f1',
 'app/src/main/java/com/blackandblue/justshare/WifiDirectServiceBroadcastReceiver.kt': 'd4b2e2d5af80110de36368a22fdac852c15cfa54d4b0837d1d571a52bd217d71',
 'app/src/main/java/com/blackandblue/justshare/data/UserPreferencesDataStore.kt': '43f9bc94b59ec4e8d607f652fee1c76e7fbe87810e0b5b5b0adc128dc748efd5',
 'app/src/main/java/com/blackandblue/justshare/data/altersend/AlterSendCrypto.kt': 'abb973dfa23e400e90f8766b67a0c40328386bfcddf207cfacfd49e6bd2f56a2',
 'app/src/main/java/com/blackandblue/justshare/data/altersend/AlterSendSocketTransfer.kt': '46e4fee7a4061d2b0c7f992cbe4667eacc658ea59f80fe7fce5035f77fdce597',
 'app/src/main/java/com/blackandblue/justshare/data/altersend/CloudflareWebSocketTransport.kt': '57e5717370a94c9ae70882278f7e079d210d6f464fcba9fdb0490be34a6933a1',
 'app/src/main/java/com/blackandblue/justshare/data/altersend/DirectSocketTransport.kt': '96b0546b07200f9921abd7c02fc2d83cc9f209d66491ed83faad67830657d0a5',
 'app/src/main/java/com/blackandblue/justshare/data/altersend/RelayTransport.kt': '5b132d1f343bd76366bd484b02198ff21d92dfc1c99ecb09d7a10d92d9bf0a56',
 'app/src/main/java/com/blackandblue/justshare/data/billing/BillingClientWrapper.kt': '4f4b93ba7d73da39904dd3cf1db6d26332d50549f53fbb5bf8997b9d7aece5ac',
 'app/src/main/java/com/blackandblue/justshare/data/billing/PurchaseRepository.kt': '29bcc246e014245bdd2324a511f6194b44c1eb4c731f1236abb34f7fb0984db1',
 'app/src/main/java/com/blackandblue/justshare/data/chat/AndroidBluetoothController.kt': '4faead2458aaf6e5ad8803a1f2482735c5ca04e4caa2410f0ee9ad95fddd3638',
 'app/src/main/java/com/blackandblue/justshare/data/chat/BluetoothDataTransferService.kt': '95087dcd113c24c0ec29e47b9765385472e9c538ca1b44d651fcd68310ec6bec',
 'app/src/main/java/com/blackandblue/justshare/data/chat/BluetoothDeviceMapper.kt': 'cfa272cc169904cb69f4c7a428f8adf9cc0bad52201a741926ad45e990e7b8b9',
 'app/src/main/java/com/blackandblue/justshare/data/chat/BluetoothFileMapper.kt': 'cf37fd4f1fce4df5c9b374a4f04e4dda57aa05573ec1b5c50d575dadf85a6be8',
 'app/src/main/java/com/blackandblue/justshare/data/chat/BluetoothMessageMapper.kt': '3ed84027da36b27dd06ca5b71fb0d21786798e4787224d16e561e617e9fd5427',
 'app/src/main/java/com/blackandblue/justshare/data/chat/BluetoothStateReceiver.kt': '3e0386bba1ab48459b2f323a4568cfd464deadac5cd220c21572e69da497c5ce',
 'app/src/main/java/com/blackandblue/justshare/data/chat/FoundDeviceReceiver.kt': '0b2c799d1d2a63cb1c6b4ae264d741b2baab986ba6758ae1a57401987388e753',
 'app/src/main/java/com/blackandblue/justshare/data/db/JediShareDatabase.kt': '81205ff80ce745d6ef181b8cc22b2a708378cb99149c753502949f8aa5acf168',
 'app/src/main/java/com/blackandblue/justshare/data/db/TransferHistoryDao.kt': '0e11087d7fc849f8e5a3024d3d6a0249fda529993cd21a5c50b4dd8c0f3b89e5',
 'app/src/main/java/com/blackandblue/justshare/data/db/TransferHistoryEntity.kt': 'c8e3d237568ca6e772da6e620fda82954e6294b2faccac87407f0ede146a2e77',
 'app/src/main/java/com/blackandblue/justshare/data/remote/QuotaApiService.kt': '067c27a58406c19a7c453d4d80b2f0e1e2614269c9a602f1fb9354a850bda723',
 'app/src/main/java/com/blackandblue/justshare/data/remote/TelemetryService.kt': '5cdf823201f6ec6e6a7ca71aafc8fe82f861212333d24ab75d8b76d7e822d881',
 'app/src/main/java/com/blackandblue/justshare/data/repository/AlterSendRepository.kt': '1020acdb567c035e5499a6be179d6d54b851568ad654f5f5c409f270ee4800d8',
 'app/src/main/java/com/blackandblue/justshare/data/repository/FileTransferRepository.kt': '8b7e340fc1cc01c16799607a2f83379b320dfe61557a546b23d5ac147caf573f',
 'app/src/main/java/com/blackandblue/justshare/data/repository/MediaRepository.kt': '310d3c739f45f50ab1eb4226a1022f617eafd4a77392da8d15664e05a68858d9',
 'app/src/main/java/com/blackandblue/justshare/data/repository/QuotaRepository.kt': '66a0f6fb89fe759a09bcfddc2dc55fed1b07866d8103c3bd2fe843677ccfc508',
 'app/src/main/java/com/blackandblue/justshare/data/repository/TransferHistoryRepository.kt': 'c12760bdf363d2edd37cbcd5dcd5cee1e446e7cfb4d37bb029b7b3eb470221c6',
 'app/src/main/java/com/blackandblue/justshare/di/AppModule.kt': '942dc638d66cc5088c675f688f8536bcdb6db4d9cba8eefeafa292ff84ca528a',
 'app/src/main/java/com/blackandblue/justshare/di/DataStoreEntryPoint.kt': '12cc7bdc53cbb5ffa7bd07749290338d3743f0da1f89bfb624cff6ab952b1dae',
 'app/src/main/java/com/blackandblue/justshare/domain/altersend/AlterSendBitmap.kt': 'e3bb45d857f1966685ee18d7d731942d45ed6756891659a2ac809c03f7ed93c8',
 'app/src/main/java/com/blackandblue/justshare/domain/altersend/AlterSendDriveEngine.kt': '09fe78a65666a046fa5aa1ddae1752400c89abac92d7cb607970d9b08663a218',
 'app/src/main/java/com/blackandblue/justshare/domain/altersend/AlterSendInvite.kt': '325aaade723e3345b62fd36c844fbd611b57504a951b15a1b293142342c67958',
 'app/src/main/java/com/blackandblue/justshare/domain/altersend/AlterSendModels.kt': '8258ee25e273d4d90c6eb29b75082a78f0e44fdb52b7089b4367216a4b0cfba6',
 'app/src/main/java/com/blackandblue/justshare/domain/altersend/AlterSendProtocol.kt': '009faa52df1ee45a2fedd43b8fb2f31f2705eba2fe9edca4a751ac4b79fc8b90',
 'app/src/main/java/com/blackandblue/justshare/domain/altersend/AlterSendRelayDirectory.kt': '1dd6c464deeb6302656dae272d87c092c8a55ea04f0831e8851eff0f1ddc8377',
 'app/src/main/java/com/blackandblue/justshare/domain/billing/DeviceIdentity.kt': 'edef7537aa69776c1bc27fbf8bdc75a71f9e6c1f010bc723ac3d873b018c35c5',
 'app/src/main/java/com/blackandblue/justshare/domain/billing/QuotaState.kt': '67820a50a88e1302a8727390209eee0ba1771b28e1f24099cf732b9abc6e2778',
 'app/src/main/java/com/blackandblue/justshare/domain/chat/BluetoothController.kt': 'cf27f8dfbdb7df8fcf5864191a8b412125c04c1969e97f28875fd72cf4025a7e',
 'app/src/main/java/com/blackandblue/justshare/domain/chat/BluetoothDevice.kt': '69f89b2638ecb18598fd70a57adf001cd127ab202f895e85ddf8615fb889cc34',
 'app/src/main/java/com/blackandblue/justshare/domain/chat/BluetoothFile.kt': '7d38e33b08e2ae832a2c2aceb5ad5354d0f341af902788152328ec523da09555',
 'app/src/main/java/com/blackandblue/justshare/domain/chat/BluetoothImage.kt': 'b9beedecd94c92b7d16b31d8d0338ad7942311286da16dbf3878cfb7c613474e',
 'app/src/main/java/com/blackandblue/justshare/domain/chat/BluetoothMessage.kt': '89369e100f88db628e35bca9265d4e6674ff801eb325fe06c476a7f96999d869',
 'app/src/main/java/com/blackandblue/justshare/domain/chat/ConnectionResult.kt': 'd8045e504f7aeae1156504a0465b10e04f611b08ac073d1666931952b3ba4679',
 'app/src/main/java/com/blackandblue/justshare/domain/chat/TransferFailedException.kt': 'c1249a5696f1d16acdcb32ac6a41ca8a99f3083ff0dae652595907f8fccc26ec',
 'app/src/main/java/com/blackandblue/justshare/domain/transfer/TransferOrchestration.kt': 'd1ae334cc93d4ed6aa3a33a1108d7ef745a587bc18368cfc73f404524351c1ff',
 'app/src/main/java/com/blackandblue/justshare/domain/transfer/TransferProgressSession.kt': '5b00733420d644c90fbe48b9f496b48d332592abc9528d01d5f7adf7181ceed6',
 'app/src/main/java/com/blackandblue/justshare/navigation/NavGraph.kt': '0c9efd27bc75b7b05e053fa86f308031347225f138f4eaad3283538aa8f62510',
 'app/src/main/java/com/blackandblue/justshare/presentation/AlterSendViewModel.kt': 'beab63638004540548302a6662377467c3fd243d9684336e77c4e0be32a47725',
 'app/src/main/java/com/blackandblue/justshare/presentation/AudioViewModel.kt': '6bf4cdb32789301331cce3afed64a91d2fd27c5d4683ddf8354255d9fcd799cf',
 'app/src/main/java/com/blackandblue/justshare/presentation/BluetoothUiState.kt': '89653b1f83acf53bb7b469e94b8e89e57d0c64433286acbe3c33f565f279dcc1',
 'app/src/main/java/com/blackandblue/justshare/presentation/BluetoothViewModel.kt': '0b0b8ca9bfd378d31e1cb362ace87b09b9dbf47a08207493e1ff6c077e83925f',
 'app/src/main/java/com/blackandblue/justshare/presentation/HistoryViewModel.kt': '2bfcad6cb7880059895ebc4f24ba9dcdc32c075b32318d79caa962cd1fd17db9',
 'app/src/main/java/com/blackandblue/justshare/presentation/ImageViewModel.kt': 'd8f72b70ae0e0a619010b309b6d4d4a347f582577513cb71bd6301126ea8daf4',
 'app/src/main/java/com/blackandblue/justshare/presentation/SelectFileViewModel.kt': '3c6e907ff24cf211bfcc0d6aa21fc5c76ff0eb9551da0dc170af838923efe485',
 'app/src/main/java/com/blackandblue/justshare/presentation/SettingsViewModel.kt': 'af7214f9708b83a013bf7dd0227a81b3d110729e9b13a46da69fc9ddc21ceb0e',
 'app/src/main/java/com/blackandblue/justshare/presentation/TransferViewModel.kt': '1b837be198e4411e9ff638a85d40bacc434a34c6b106e1caf2e124a2d0349913',
 'app/src/main/java/com/blackandblue/justshare/presentation/VideoViewModel.kt': '49fad169746bcb487224ca7535571e68aa5a9b295a39c0cededb993d1b2d1829',
 'app/src/main/java/com/blackandblue/justshare/presentation/WifiDirectViewModel.kt': '5529e19fecf63cd7c1286b6103502c1809a55fea06eba786ba40bda6722b96d6',
 'app/src/main/java/com/blackandblue/justshare/presentation/billing/BillingViewModel.kt': '7f6f25e29fa4da274995a42c18b5f4aad9cf5a194a618c0321f50b1d06a4fac4',
 'app/src/main/java/com/blackandblue/justshare/presentation/components/ChatMessage.kt': 'bab54a67d54d7a8b3199aff4546913a9b22c3b9daabefecd04f3f7139a2f010e',
 'app/src/main/java/com/blackandblue/justshare/ui/components/BottomNavBar.kt': 'e1f4e1ac70f0fa195605667416003673b7b2b8daa61ec2f4a228ecb5078d7601',
 'app/src/main/java/com/blackandblue/justshare/ui/components/DesignSystem.kt': 'a4a2984169a07d4dc0776b6ff133968d9f1aa5ed42577ef17247b0328794fe59',
 'app/src/main/java/com/blackandblue/justshare/ui/components/EncryptedBadge.kt': '4ec3e04fe41b99621fe58f01a7ee9282b912c079eda8ba930bf564bbd1a90d26',
 'app/src/main/java/com/blackandblue/justshare/ui/components/HardwareStateWrapper.kt': '7143621afa6cd304c3bf15828eb716eb78f831a4d941d303723e4b95c1b7cab2',
 'app/src/main/java/com/blackandblue/justshare/ui/components/QuotaExhaustedDialog.kt': '1668799dddf4a6daf216ab6aa492f93df88113b1a9fda0aafd288152e32fcedf',
 'app/src/main/java/com/blackandblue/justshare/ui/components/QuotaIndicatorBanner.kt': '74a2e02746f81bffeec10dc236bf6dc28e15fbcdf8fc980393f3882041408999',
 'app/src/main/java/com/blackandblue/justshare/ui/components/TransferExitGuard.kt': 'f5a68570eda56924fe9f0ff6b4aab3136b470d471e2c358a089cb1016af0a23e',
 'app/src/main/java/com/blackandblue/justshare/ui/screens/AlterSendScreen.kt': '7d306a1ac13cf59c6c0dcee460598d9bbd07fa30af68ccbf64e901abb69a0ed6',
 'app/src/main/java/com/blackandblue/justshare/ui/screens/DiscoverDevicesScreen.kt': 'c3d7fca3900417b90449ea579fffbbc3e7d55c2edee3442e69a3252bbbeeae70',
 'app/src/main/java/com/blackandblue/justshare/ui/screens/HistoryScreen.kt': '7a29c4c97d9b319e62cbe0ce3c442b1d117fc1673a7edaa0d950cd6a30a4290d',
 'app/src/main/java/com/blackandblue/justshare/ui/screens/HomeScreen.kt': 'ab979ef699d95e12e1f97c36682b4d7a368c0372c4eb51f25181b2bc9820b50e',
 'app/src/main/java/com/blackandblue/justshare/ui/screens/OnboardingScreen.kt': '1cc12203101b322537bcde1912b600d1f058001d92a3bfe2a5760eb72c18283d',
 'app/src/main/java/com/blackandblue/justshare/ui/screens/PermissionsScreen.kt': '01b2e1990eaf2ed4b5ba69f2c642f8f5da73412958338bb56a968a10f066b787',
 'app/src/main/java/com/blackandblue/justshare/ui/screens/RemoteTransferProgressScreen.kt': 'ebc6def8d78654a9a38aae80a571ce8787e6c4def3a1accfc8c35497c75b7647',
 'app/src/main/java/com/blackandblue/justshare/ui/screens/ScanQrScreen.kt': '5922d58e57004b5431053a564adf98383232485971373bc3fb93b7244c69f985',
 'app/src/main/java/com/blackandblue/justshare/ui/screens/SelectFilesScreen.kt': '1ada74757fd8098209a882951b005bfd2aa52b4a32d5af526c05b044e35916e3',
 'app/src/main/java/com/blackandblue/justshare/ui/screens/SettingsScreen.kt': '19ae1dd29575a73cd7986107bb691a13a0e778165337cd192458724e265d7b91',
 'app/src/main/java/com/blackandblue/justshare/ui/screens/SplashScreen.kt': '5d6b20304bc1905381eaa8bca60f183d6af2c447b3a5ee488fd90455a44cccff',
 'app/src/main/java/com/blackandblue/justshare/ui/screens/TransferProgressScreen.kt': '96e432106cc1b55530ee8186bcb929c2cce523b684671306f9814ca7a81cd6b2',
 'app/src/main/java/com/blackandblue/justshare/ui/theme/Color.kt': 'e03a770b53af7a8a642c142c4f2e60df7a038f872b7949adfa417ebd14d9ecae',
 'app/src/main/java/com/blackandblue/justshare/ui/theme/Shape.kt': 'ff0e79cf8ebd73ff7c08b235b12960d0bfccc6f671b24f40d64ebba9b6e0fe99',
 'app/src/main/java/com/blackandblue/justshare/ui/theme/Theme.kt': 'd357a7f5e6632114d4c924184d4d943ff908870c496e2365c68489d68b66514a',
 'app/src/main/java/com/blackandblue/justshare/ui/theme/Type.kt': '0ef99b61393f2fcb8327c08938328717399e3cfad14e97b6fb64a49987a00c79',
 'app/src/main/res/drawable-night/app_logo_mark.xml': 'a449bb414df8da3ed9430afeecd9d249f20c12ec310f87b967c994afe0227d41',
 'app/src/main/res/drawable-night/ic_launcher_foreground.xml': 'f9371415c2a82d4ce066f4564823233963e013ca1c9fcfac0a77f3b9fb92c8c1',
 'app/src/main/res/drawable/app_logo_full.png': '2414ae863eff8dacd02d8390b8e2c1773b9e23fd95aae5d57cfb4548c48aa63c',
 'app/src/main/res/drawable/app_logo_mark.xml': '7f8c43fb5df00087a562c62b7267371d19caeb1870c5bcdd0cb03a78e742d850',
 'app/src/main/res/drawable/bluetooth_24.xml': '9725772eb26bc075b192e717f50b300fd29c9f11447dfb7e43043960a3a004c3',
 'app/src/main/res/drawable/document_icon.png': '2371b75bf1c5b6f7e7c828f83064399a6603ad4c7b227af111cb0cae9126454c',
 'app/src/main/res/drawable/history_icon.xml': 'fe1a0163b34752d5295da62d527472adba3af607a8657ffaf923e62df57e2186',
 'app/src/main/res/drawable/ic_launcher_foreground.xml': 'f9371415c2a82d4ce066f4564823233963e013ca1c9fcfac0a77f3b9fb92c8c1',
 'app/src/main/res/drawable/img.png': '3fa6c26c487503b763ffeb3518737a6b40572616bf8fab6f8a164cc33abc8a1f',
 'app/src/main/res/drawable/main_activity_illustration.png': 'bd6778e0b692a262de34be3ee9c7acd781634bb47d92cbcc129e377885b4e981',
 'app/src/main/res/drawable/main_activity_image.png': 'd61e63a21e9c96245f8c157fd799816848e66d2b87cc77ab78dd1ddd6e5beac3',
 'app/src/main/res/drawable/music_icon.png': '4558467d10119a542b6360579afcf772d687e03613b4a00fce131e3fedb2b25a',
 'app/src/main/res/drawable/photo_icon.png': '3f96195d2997fca67348edd4425ab6a192c005cd2be814fc3e769cf813f332d1',
 'app/src/main/res/drawable/receive.xml': 'c39cfbac4584456e105ad106d20a1203f164412f231b518bc43e77d09209de75',
 'app/src/main/res/drawable/send.xml': '2e8d19a7121630f3a617140ddee32746f21244aabb44330bc0a8ad5d823d3a29',
 'app/src/main/res/drawable/splash_screen_image.png': '143f8f972d57ce3b31c61ee1720c0d8707bf97e14831769cc1b8e73f8cedb605',
 'app/src/main/res/drawable/video_icon.png': '26ee6a991422eaa442f875efae57133729b67be188c255c83a5c568e2a8194ce',
 'app/src/main/res/drawable/welcome_image.jpeg': '062cf8adc77f3c54f96db94ae7051c9b2499e20600a310e4562c7fd988b215d5',
 'app/src/main/res/drawable/wifi_icon.xml': 'f1bfd24a140e6f4105819309690ce098947c169d976ffdf5c0ffbb1e2437a53c',
 'app/src/main/res/font/roboto_serif_regular.ttf': 'ae870616f883b21ff4f22b833cfeefce5de340ba4eab48f1f024a9789e8ea1c9',
 'app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml': '26310833a35076dfe3bdcd9fa469c5c7e36c26623eaf6009bf2d450f779189a3',
 'app/src/main/res/mipmap-hdpi/ic_launcher.png': '9c2b7f2a75132942e30445bf77afc003475d3e0b10f2e04daf9dc72de01f9775',
 'app/src/main/res/mipmap-hdpi/ic_launcher_background.png': '685de69e37080a327f7fb28f623fb1405cc742fdc6c23c68147e97e208ef7985',
 'app/src/main/res/mipmap-hdpi/ic_launcher_foreground.png': '3bf4e62cebdbab6bc42b179fa1b6aaff629dcaff8b07e8d0a9dc5f5744920f89',
 'app/src/main/res/mipmap-hdpi/ic_launcher_monochrome.png': 'cd31c8594d8a027d750c0499e7b36cf9aaf78c8abf4c7282ba5c8e8e48a36cd8',
 'app/src/main/res/mipmap-hdpi/ic_launcher_round.png': '4ec59322453d1533b101812e244d9feb98c7ce01c35b64a1e97dd4458a1052da',
 'app/src/main/res/mipmap-mdpi/ic_launcher.png': 'b2ef19c6c200c3eb3fdf7aa7314016eadb0ebae9ca60c1f8d76106811196e59b',
 'app/src/main/res/mipmap-mdpi/ic_launcher_background.png': '3f38afaaa2fd816130c259df2643676b580258e6983ea9f26766335f252a4372',
 'app/src/main/res/mipmap-mdpi/ic_launcher_foreground.png': '892820af7fa47fec25de9391f46d9c07fc4abb30b40293405609c2dfdef5f6aa',
 'app/src/main/res/mipmap-mdpi/ic_launcher_monochrome.png': '871c39e938dac334f13bb337c53d5f37ce3217688dd472b65125d12b46535807',
 'app/src/main/res/mipmap-mdpi/ic_launcher_round.png': '31c9957bf2216e3d05b9eb83fda177503fe7e21443035cf34c4b69e4781b8fa8',
 'app/src/main/res/mipmap-xhdpi/ic_launcher.png': '00c9f68606462caced002dad6dcf1d3d716b425746feca71022ab85d6769c607',
 'app/src/main/res/mipmap-xhdpi/ic_launcher_background.png': '27ca37a7b6ee7e0f162d153bd9f810f65372d864e5914ef37d45aefa8743c7eb',
 'app/src/main/res/mipmap-xhdpi/ic_launcher_foreground.png': 'ac51b7510e077586c126ec613fae9484950675e020c964f97f99514bd0a67d62',
 'app/src/main/res/mipmap-xhdpi/ic_launcher_monochrome.png': '331247beb37d85eda3dc8ca8565db6aafd69857fc8faec8a5cf2560746654e6a',
 'app/src/main/res/mipmap-xhdpi/ic_launcher_round.png': 'e911508783055c52dc8288534d7c5b3dc9c1bcd681297b834849dd1928609fce',
 'app/src/main/res/mipmap-xxhdpi/ic_launcher.png': '0a8c29b1fa8b27970faf2b3c7e63894e3c6e100fb2050859c77a025935403290',
 'app/src/main/res/mipmap-xxhdpi/ic_launcher_background.png': '8313ad594d68fe6b6feea6b8a743568d7f88671f0401365fe6346926a4bfb4ac',
 'app/src/main/res/mipmap-xxhdpi/ic_launcher_foreground.png': '2bf9ff92532caed1dbba22467d78f4fffcf1acb3ab3217b7592f347106b71a8c',
 'app/src/main/res/mipmap-xxhdpi/ic_launcher_monochrome.png': '3e8a02cdbb63fa9149712b5a2aa24ff894d1946b75d0d147b1cbc53ed8989665',
 'app/src/main/res/mipmap-xxhdpi/ic_launcher_round.png': 'a162bec66e701aca706367b58984098cf0d67d848c0eba63d317041774915547',
 'app/src/main/res/mipmap-xxxhdpi/ic_launcher.png': '3dec5ac50a95c8b2ee6cd25f0346a570f8fdb37668754e18aa3846dd026eef1b',
 'app/src/main/res/mipmap-xxxhdpi/ic_launcher_background.png': '6c6ccbbf2822475b2915dcdeaf1d677cc5e7f1d40a70e06cd5e089e6b27e2f5b',
 'app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png': '710f46dc984d4ec715eec685f5f79428c38c392f601bcca30c2174089ffafb80',
 'app/src/main/res/mipmap-xxxhdpi/ic_launcher_monochrome.png': '926facdee2d88ccbcb5df35d05434b6327bd732a070c206a43a35ec0cb887dc8',
 'app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png': 'fd3a4ac5542bfaf86d79ebd7667706f7e1b8cae793db8d9f6c5621bf7f969688',
 'app/src/main/res/raw/connecting_animation.json': 'b2e989e8d7035e9cb57cbeb51e87e457f4946dfca1009e3da5901861f7e0860a',
 'app/src/main/res/raw/connecting_text_animation.json': '08055c683454fab9ee3b3636c3fcac98898ce6c01c23779883577e571c6bd265',
 'app/src/main/res/raw/done_tick_animation.json': '9a367d38c7bb2a89ca2e0a5033fcdffc30ab697771017370e8adc981e4caa6ab',
 'app/src/main/res/raw/file_transfer_animation.json': 'dae9ae82a62136c1ecf961b5de218933b24824c26a86661ecf803ad144070f5a',
 'app/src/main/res/raw/welcome_activity_animation.json': 'eb3ff685a29e03ffe1b14c2083bc36696f65ae84f59128a92605f95709481933',
 'app/src/main/res/values-night/colors.xml': 'ea7e04cbe0304f41c272512fddb2c20082d1a120eb8277c04e55322f20846fce',
 'app/src/main/res/values-night/ic_launcher_background.xml': 'b5ac8a081d33ff53462c07829e21f07e7e22d3ba223fb6e684407ac170317fae',
 'app/src/main/res/values-v31/themes.xml': '9f391219afe0d450b0f17fe2c7d68f323bac2c0770f6153468a4af48b9bfc742',
 'app/src/main/res/values/colors.xml': 'f0220d25fd10b430b0f9c9eaef09b0d1c7fddcc7de7105248971a706088fe87c',
 'app/src/main/res/values/ic_launcher_background.xml': 'b5ac8a081d33ff53462c07829e21f07e7e22d3ba223fb6e684407ac170317fae',
 'app/src/main/res/values/strings.xml': 'f931bc4df8c626dacf23c159ad34cb5f8ac07b0370af4038a27f2bf463e01e0a',
 'app/src/main/res/values/themes.xml': 'c535be7075c8fc3d388488589311a738c69979cd2a482d69fb7418b531cd9c7a',
 'app/src/main/res/xml/backup_rules.xml': '2c5c697984f4c52b526337ab61113f5843e0bfc6af4722374916ffe6da38dd9a',
 'app/src/main/res/xml/data_extraction_rules.xml': 'cb029b35db0e976c087100424e16e14a6256b46962398887520e51508c3b5842',
 'app/src/release/java/com/blackandblue/justshare/AppCheckInstaller.kt': '43da5ef0909d9d9ad28630dd753396cdd4fae838b3d54925b481000dfb30c2bf',
 'app/src/test/java/com/blackandblue/justshare/ExampleUnitTest.kt': '177c981ded99195b8f060dc078072192066c2ed91990817cfbc4a177a7a34b02',
 'app/src/test/java/com/blackandblue/justshare/JustShareTelemetryTest.kt': 'ca342ecc1fdf8e57c7f97c1471fe089f74a7a1f3ab866d4c4135d212d1bae81a',
 'app/src/test/java/com/blackandblue/justshare/LocalTransferPermissionsTest.kt': '34a285acbc4c9e7de469dbea8e1cf91cfe8841ee0ccadbec4eeb712ad3e7dbd8',
 'app/src/test/java/com/blackandblue/justshare/data/altersend/AlterSendCryptoTest.kt': '491ae23e3f3e4c24de807de258ce125acd57506a460383a5f1537d7fb0f5bcd8',
 'app/src/test/java/com/blackandblue/justshare/data/altersend/CloudflareWebSocketTransportTest.kt': '70c4d79368a7da7cb34965f65c485385a68a1c52cfdfcf7ac79529ee30f5f3b3',
 'app/src/test/java/com/blackandblue/justshare/domain/altersend/AlterSendBitmapTest.kt': '9fe987380a2975f9f05bdf4b126dd5e57197f9a2421b18ba520e07eb29f93432',
 'app/src/test/java/com/blackandblue/justshare/domain/altersend/AlterSendDriveEngineTest.kt': '11a0a1a4242384b8eb9165a084a7cbceaf87cbbfd6103a645780afc51442ddc2',
 'app/src/test/java/com/blackandblue/justshare/domain/altersend/AlterSendProtocolTest.kt': 'c0737b80377b28ab8278f39c8114133680f5dc7b3761d527536cb0a24b0a5208',
 'app/src/test/java/com/blackandblue/justshare/domain/altersend/AlterSendRelayDirectoryTest.kt': '7710d1f774bbe39ab9a5dc1c56ea0564198e46e1563a669566b443d8237f0fbc',
 'app/src/test/java/com/blackandblue/justshare/domain/transfer/TransferOrchestrationTest.kt': 'd2bbd287cd2355a37188a7a4a6f9d4e58f08eb0e371825205c1187a3d5045f91',
 'app/src/test/java/com/blackandblue/justshare/domain/transfer/TransferProgressSessionTest.kt': '09fd1b5698c920bdb5165d726117bb56c1a542649f1777f3e5dc6e1d733d5340'}
REVIEWED_BUILD_BLOBS = {'app/build.gradle': 'fc96bb96fbbc9dd3132f4612bea8d8a001860d22',
 'app/proguard-rules.pro': '4782b0383c0407478b0f989a204a5122c9d7f614',
 'build.gradle': 'ef5476085bd025313b867c9bc96359bab7b30ee4',
 'gradle.properties': '627d7b878a2a88981c65c9af3b594600e95fe136',
 'gradle/wrapper/gradle-wrapper.jar': 'e708b1c023ec8b20f512888fe07c5bd3ff77bb8f',
 'gradle/wrapper/gradle-wrapper.properties': 'c274d8dcd4e0ac50dfa93f8fb0db9af60183c85c',
 'gradlew': '4f906e0c811fc9e230eb44819f509cd0627f2600',
 'gradlew.bat': '107acd32c4e687021ef32db511e8a206129b88ec',
 'settings.gradle': '563c426d2a221daa6b72a362c426f07acb7ce70c'}
REVIEWED_BUILD_SHA256 = {'app/build.gradle': 'f50a723037e543135cc04efaa3164dd9ae7cf758d7631952de0ee7499b1355e7',
 'app/proguard-rules.pro': '8a8269d06a7f2fba764340904c3a7f23655bc6299e855c7352178ede4eceba20',
 'build.gradle': '3f2f6e900c236549cb371311b00253ccf930f7852f99514baefb240ca02ab18c',
 'gradle.properties': 'f3f77d5f4ac2622e5ba6b48b01cdd20ec4b9fd8622b3f7d4b76a57f511ee347f',
 'gradle/wrapper/gradle-wrapper.jar': 'e996d452d2645e70c01c11143ca2d3742734a28da2bf61f25c82bdc288c9e637',
 'gradle/wrapper/gradle-wrapper.properties': '1896801cf5fb078319cbb70a8917ac171210fe46b7749c696f011593332126dc',
 'gradlew': '63135287117a1e6d12c84580f1f49c61d1ba02218ecd28660605e97f976e7d65',
 'gradlew.bat': 'c46a27c79007746de5922b17abb6230d64ad8b1ba3ad1585ee5c6543c2a9b129',
 'settings.gradle': '61bff6e9290e07e60d7fe0a05fde0f03ae177f95f9485bfbcd0e20b4f04ad475'}
REVIEWED_CONFIGURED_PINS = {'configured_environment_sha256': {'ANDROID_HOME': None,
                                   'ANDROID_PREFS_ROOT': None,
                                   'ANDROID_SDK_HOME': None,
                                   'ANDROID_SDK_ROOT': None,
                                   'ANDROID_USER_HOME': None,
                                   'CLASSPATH': None,
                                   'GRADLE_OPTS': None,
                                   'GRADLE_USER_HOME': None,
                                   'HOME': 'b8a82e9f9db7cfda15cdb757d7e8fb90b29a927186f323583b4364c227295ab1',
                                   'JAVA_HOME': None,
                                   'JAVA_OPTS': None,
                                   'JDK_JAVA_OPTIONS': None,
                                   'PATH': 'f63ceeedec246f962421d434b34110a1284aaab8abc1158db2ca07ad3cb552b3',
                                   'QUOTA_API_BASE_URL': None,
                                   'USERPROFILE': None,
                                   '_JAVA_OPTIONS': None},
 'configured_external_files': {'/Users/speketi/.gradle/gradle.properties': None,
                               '/Users/speketi/.gradle/init.gradle': None,
                               '/Users/speketi/.gradle/init.gradle.kts': None},
 'configured_files': {'.env': None,
                      '/Users/speketi/Projects/BlackAndBlue/.release-secrets/justshare/credentials.properties': '43a2eec5dd6b48acd3a543439bc230f436a5d3ea889192bcd8c6af9020803fa4',
                      'app/google-services.json': 'b96f375fe98d1e8e7ebdd6e2d2f2f27552fd884ec1ea1d0da97b8252cafe35dd',
                      'local.properties': 'b22e2e00ec007a06f35b0a60808501b03b6c913ddb8360e4a499656debe086ab'},
 'helper_inputs_sha256': '60452b0711b86e51f836f6a2d9365bf28a04bcce5318d39a54d8ee9c9182fb7a',
 'helper_sha256': 'b7667e7646e9e0a6d650fecdfdc8a4563f5a1153d068125c781744684283202e',
 'java_binary_sha256': '94b66f2cc8edfba9e0e8b25abe93e4c3617d2cc1af7e8eaec0f5b2afe0df5c12',
 'jdk_release_sha256': '88e5e19520ee8f74d719cdffb6b827f1d7575261d7c3b89e614c67a4d5290cb7'}
REVIEWED_ANDROID_DECLARATIONS = {'com.blackandblue.justshare.data.chat.BluetoothDiscoveryPermissionTest': {'count': 3,
                                                                           'executed': 0,
                                                                           'methods': ['liveGrantRevocationRejectsProtectedMetadataAndUnregistersFoundReceiver',
                                                                                       'protectedGetterRaceIsCaughtAndQueuedCallbacksStayInactive',
                                                                                       'stopWithoutScanGrantUnregistersAndRegrantRegistersOnceAgain'],
                                                                           'source': 'app/src/androidTest/java/com/blackandblue/justshare/data/chat/BluetoothDiscoveryPermissionTest.kt',
                                                                           'status': 'UNRUN'},
 'com.blackandblue.justshare.navigation.LocalTransportOwnershipTest': {'count': 3,
                                                                       'executed': 0,
                                                                       'methods': ['immediateHomeReentryReleasesOldLeaseWhileOutgoingDiscoveryStillFades',
                                                                                   'receiverProgressReusesDiscoveryOwnerAndRevocationKeepsReceiveDirection',
                                                                                   'senderProgressReusesDiscoveryOwnerAndRevocationStopsItWithoutLosingFiles'],
                                                                       'source': 'app/src/androidTest/java/com/blackandblue/justshare/navigation/LocalTransportOwnershipTest.kt',
                                                                       'status': 'UNRUN'},
 'com.blackandblue.justshare.navigation.PermissionRecoveryNavigationTest': {'count': 5,
                                                                            'executed': 0,
                                                                            'methods': ['directShareDenialCanBrowseAndResumeDoesNotBounceNonlocalPages',
                                                                                        'foregroundRevocationDisposesDiscoveryBeforeRecoveryAndCanResume',
                                                                                        'otherTransportGrantCannotConstructBluetoothDiscovery',
                                                                                        'receiverRoleSurvivesDirectEntryRecoveryWithoutInventingFiles',
                                                                                        'selectedFilesAndSenderRoleSurviveDenialSettingsAndReturnToPicker'],
                                                                            'source': 'app/src/androidTest/java/com/blackandblue/justshare/navigation/PermissionRecoveryNavigationTest.kt',
                                                                            'status': 'UNRUN'},
 'com.blackandblue.justshare.presentation.TransferRetryStateTest': {'count': 4,
                                                                    'executed': 0,
                                                                    'methods': ['cancelledAndQueuedOldProgressCannotBlockSelectedFileRetry',
                                                                                'fastIncomingCompletionSurvivesTheNavigationStart',
                                                                                'permissionInterruptionInvalidatesOldUpdatesAndRetainsExactSelectionForRetry',
                                                                                'permissionLossAfterFastIncomingCompletionDoesNotReplaceTheFinishedResult'],
                                                                    'source': 'app/src/androidTest/java/com/blackandblue/justshare/presentation/TransferRetryStateTest.kt',
                                                                    'status': 'UNRUN'},
 'com.blackandblue.justshare.presentation.WifiPermissionRecoveryTest': {'count': 3,
                                                                        'executed': 0,
                                                                        'methods': ['deniedLateConnectionAndDiscoveryCallbacksCannotStartLocalService',
                                                                                    'revocationStopsExistingServiceAndRejectsQueuedConnectionCallback',
                                                                                    'settingsRecoveryTargetsOnlyThisAppsPermissionPage'],
                                                                        'source': 'app/src/androidTest/java/com/blackandblue/justshare/presentation/WifiPermissionRecoveryTest.kt',
                                                                        'status': 'UNRUN'},
 'com.blackandblue.justshare.ui.screens.PermissionsScreenTest': {'count': 3,
                                                                 'executed': 0,
                                                                 'methods': ['finalPermissionAndRecoveryActionsRemainAccessible',
                                                                             'returningFromSettingsRefreshesActualGrantsWithoutAutoNavigation',
                                                                             'unavailableSettingsKeepsRecoveryAndBrowseExitUsable'],
                                                                 'source': 'app/src/androidTest/java/com/blackandblue/justshare/ui/screens/PermissionsScreenTest.kt',
                                                                 'status': 'UNRUN'}}
FILES = ["build.gradle", "settings.gradle", "gradle.properties", "gradlew", "gradlew.bat",
         "gradle/wrapper/gradle-wrapper.properties", "gradle/wrapper/gradle-wrapper.jar",
         "app/build.gradle", "app/proguard-rules.pro", SELF]
CONFIGURED_FILES = ["local.properties", ".env", "app/google-services.json"]
ANDROID_SOURCES = [
    "app/src/androidTest/java/com/blackandblue/justshare/ui/screens/PermissionsScreenTest.kt",
    "app/src/androidTest/java/com/blackandblue/justshare/navigation/PermissionRecoveryNavigationTest.kt",
    "app/src/androidTest/java/com/blackandblue/justshare/presentation/WifiPermissionRecoveryTest.kt",
    "app/src/androidTest/java/com/blackandblue/justshare/presentation/TransferRetryStateTest.kt",
    "app/src/androidTest/java/com/blackandblue/justshare/data/chat/BluetoothDiscoveryPermissionTest.kt",
    "app/src/androidTest/java/com/blackandblue/justshare/navigation/LocalTransportOwnershipTest.kt",
]
WATCH = None

def command(*argv, timeout=None):
    bound = 10 if WATCH is None else min(10, WATCH.deadline - time.monotonic())
    if timeout is not None:
        bound = min(bound, timeout)
    if bound <= 0:
        raise TimeoutError("The unchanged 300-second batch deadline has expired")
    return subprocess.check_output(["rtk", "proxy", *argv], cwd=REPO, text=True, timeout=bound).strip()


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def configured_files():
    paths = {name: REPO / name for name in CONFIGURED_FILES}
    signing = os.environ.get("JUSTSHARE_SIGNING_PROPERTIES_FILE")
    if not signing and (REPO / "local.properties").is_file():
        properties = {}
        for line in re.split(r"\r\n|\r|\n", (REPO / "local.properties").read_bytes().decode("latin-1")):
            line = line.lstrip(" \t\f")
            if not line or line.startswith(("#", "!")):
                continue
            if "\\" in line or "=" not in line:
                raise ValueError("Unsupported configured properties syntax; inspect privately")
            key, value = line.split("=", 1)
            key = key.rstrip(" \t\f")
            if not re.fullmatch(r"[A-Za-z0-9_.-]+", key) or key in properties:
                raise ValueError("Invalid or duplicate configured properties key")
            properties[key] = value.lstrip(" \t\f")
        signing = properties.get("JUSTSHARE_SIGNING_PROPERTIES_FILE")
    if signing:
        path = Path(signing)
        if not path.is_absolute():
            path = REPO / path
        paths[str(path)] = path
    return {name: sha(path) if path.is_file() else None for name, path in sorted(paths.items())}


def source_files():
    return {path.relative_to(REPO).as_posix(): sha(path)
            for name in TREES for path in sorted((REPO / name).rglob("*")) if path.is_file()}



def configured_external_files():
    gradle_home = Path(os.environ.get("GRADLE_USER_HOME", str(Path.home() / ".gradle"))).expanduser()
    paths = [gradle_home / "gradle.properties", gradle_home / "init.gradle", gradle_home / "init.gradle.kts"]
    init_directory = gradle_home / "init.d"
    if init_directory.is_dir():
        paths.extend(path for path in sorted(init_directory.rglob("*")) if path.is_file())
    return {str(path): sha(path) if path.is_file() else None for path in paths}


def fingerprints(require_clean=True):
    command("git", "ls-files", "--error-unmatch", "--", *FILES)
    dirty = command("git", "-c", "core.fsmonitor=false", "status", "--porcelain",
                    "--untracked-files=all", "--", *TREES, *FILES)
    if require_clean and dirty:
        raise ValueError("Compiled sources, configured build/wrapper inputs and caller must be committed and clean")
    env_keys = {"JAVA_HOME", "JAVA_OPTS", "GRADLE_OPTS", "GRADLE_USER_HOME", "ANDROID_HOME", "ANDROID_SDK_ROOT", "QUOTA_API_BASE_URL", "PATH",
                "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS", "CLASSPATH", "HOME", "USERPROFILE",
                "ANDROID_USER_HOME", "ANDROID_SDK_HOME", "ANDROID_PREFS_ROOT"}
    env_keys.update(key for key in os.environ if key.startswith(("JUSTSHARE_", "ALTERSEND_", "CF_RELAY_", "ORG_GRADLE_PROJECT_")))
    return {"source": command("git", "rev-parse", "HEAD"),
            "tree": command("git", "rev-parse", "HEAD^{tree}"),
            "source_build_caller_dirty": dirty,
            "source_trees": {name: command("git", "rev-parse", "HEAD:" + name) for name in TREES},
            "source_files": source_files(),
            "build_blobs": {name: command("git", "rev-parse", "HEAD:" + name) for name in REVIEWED_BUILD_BLOBS},
            "caller_blob": command("git", "rev-parse", "HEAD:" + SELF),
            "caller_working_blob": command("git", "hash-object", "--", SELF),
            "files": {name: sha(REPO / name) for name in FILES},
            "configured_files": configured_files(),
            "configured_external_files": configured_external_files(),
            "configured_environment_sha256": {key: hashlib.sha256(os.environ[key].encode()).hexdigest()
                                              if key in os.environ else None for key in sorted(env_keys)},
            "jdk_release_sha256": sha(JAVA_HOME / "release"), "java_binary_sha256": sha(JAVA_HOME / "bin/java"),
            "helper_sha256": sha(HELPER), "helper_inputs_sha256": sha(HELPER_INPUTS),
            "packaging_inputs": packaging_inputs()}




def declared_android_methods():
    expected = {}
    for path in ANDROID_SOURCES:
        source = (REPO / path).read_text()
        package = re.search(r"(?m)^package ([\w.]+)\s*$", source)
        name = Path(path).stem
        if (not package or not re.search(r"(?m)^class " + re.escape(name) + r"\b", source)):
            raise ValueError("Reviewed Android package/class changed: " + path)
        annotations = len(re.findall(r"(?m)^\s*@Test\s*$", source))
        matches = re.findall(r"@Test\s+fun\s+(?:`([^`]+)`|(\w+))", source)
        methods = [quoted or plain for quoted, plain in matches]
        if not methods or len(methods) != annotations or len(set(methods)) != annotations:
            raise ValueError("Selected Android declarations are not exact/unique: " + path)
        expected[package.group(1) + "." + name] = {
            "source": path, "count": annotations, "methods": sorted(methods), "executed": 0, "status": "UNRUN"}
    return expected


def check_layout():
    root = (REPO / "build.gradle").read_text()
    app = (REPO / "app/build.gradle").read_text()
    wrapper = (REPO / "gradle/wrapper/gradle-wrapper.properties").read_text()
    if ("id 'com.android.application' version '8.10.1'" not in root
            or "id 'org.jetbrains.kotlin.android' version '2.0.21'" not in root
            or "gradle-8.11.1-bin.zip" not in wrapper):
        raise ValueError("Configured AGP/Kotlin/wrapper changed; replacement toolchains are forbidden")
    for value in ("id 'com.android.application'", "id 'org.jetbrains.kotlin.android'",
                  "testImplementation 'junit:junit:4.13.2'",
                  "androidTestImplementation 'androidx.test.ext:junit:1.2.1'",
                  'testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"'):
        if value not in app:
            raise ValueError("Configured debug/host-test/Android-test variant layout changed")
    if ("include ':app'" not in (REPO / "settings.gradle").read_text()
            or not re.search(r'(?m)^JAVA_VERSION="21(?:[.\"]|$)', (JAVA_HOME / "release").read_text())):
        raise ValueError("Configured app module or local JDK 21 changed")




def executable_inventory(timeout=1):
    if timeout is None:
        raw = command("ps", "-axo", "pid=,lstart=,comm=", timeout=1)
    else:
        if timeout <= 0:
            raise TimeoutError("The batch work deadline has expired; executable inventory refused")
        raw = subprocess.check_output(
            ["rtk", "proxy", "ps", "-axo", "pid=,lstart=,comm="],
            cwd=REPO, text=True, timeout=min(1, timeout)
        ).strip()
    inventory = {}
    for line in raw.splitlines():
        parts = line.split(None, 6)
        if len(parts) != 7 or not parts[0].isdigit():
            raise ValueError("Malformed process executable inventory; admission refused")
        pid = int(parts[0])
        if pid in inventory:
            raise ValueError("Duplicate process identity in executable inventory; admission refused")
        inventory[pid] = {"basename": Path(parts[6]).name,
                          "identity": hashlib.sha256(" ".join(parts[1:6]).encode()).hexdigest()}
    if not inventory:
        raise ValueError("Empty process executable inventory; admission refused")
    return inventory


def heavy(rows, owned=None, executables=None, timeout=None):
    owned = {} if owned is None else owned
    executables = executable_inventory(timeout=timeout) if executables is None else executables
    foreign = []
    for pid, current in executables.items():
        executable = current["basename"]
        identity = current["identity"]
        name = executable.casefold()
        if name not in ("java", "java.bin") and not name.startswith(("emulator", "avd", "qemu", "godot")):
            continue
        row = rows.get(pid, {})
        identity_conflict = row.get("identity") is not None and row["identity"] != identity
        if row.get("identity") != identity:
            row = {}  # Do not attach metadata belonging to an exited/reused PID.
        if not identity_conflict and owned.get(pid) == identity:
            continue
        foreign.append({"pid": pid, "pgid": row.get("pgid"), "identity_sha256": identity,
                        "command_sha256": row.get("commandHash"),
                        "executable_basename": re.sub(r"[^A-Za-z0-9._-]", "_", executable)[:128]})
    return foreign


def write_private(path, data):
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    with os.fdopen(descriptor, "wb") as stream:
        stream.write(data)


def json_bytes(value):
    return (json.dumps(value, indent=2) + "\n").encode()









SDK_TOOLS = Path("/Users/speketi/Library/Android/sdk/build-tools/36.0.0")
DEBUG_KEYSTORE = Path("/Users/speketi/.android/debug.keystore")
TEST_MANIFEST = REPO / "app/build/intermediates/packaged_manifests/debugAndroidTest/processDebugAndroidTestManifest/AndroidManifest.xml"
TEST_MANIFEST_VERSION = {'versionCode': '', 'versionName': ''}
HOST_CALLER = REPO / "docs/run-permission-recovery-host-validation-20261006.py"
HOST_CALLER_SHA = "fb362f7af2bae50cd21852f2aec1f5b5c09e7c775b28974543e905fe0c798525"
HOST_RESULT = REPO / "docs/permission-recovery-host-validation-20261006-one/result.json"
HOST_RESULT_SHA = "7a4d7499a1c33f38d516cb86fc3ec294b9cde978fe7f097b1b972776faa88b79"
HOST_VALIDATION_HEAD = "4e35e47fee047a6cfafd92079aacdc0c21df9efb"
HOST_TEST_APP_SOURCE = "965536c13b4678aa302f75f100ecab52e065f857"
HOST_SOURCE_TREES = {"app/src": "658642c649f9f888804012e0aa3b6e713d24ccf2"}
HOME_SUCCESSOR_SOURCE_DELTA = "app/src/main/java/com/blackandblue/justshare/ui/screens/HomeScreen.kt"
PREPARATION_BASE_HEAD = 'd66c25bf172fc821052298f34a6936bbf3b925a3'
PACKAGING_ADMISSION_READY = True
ASSEMBLE_TASKS = [":app:assembleDebug", ":app:assembleDebugAndroidTest"]
PACKAGE_TASKS = [":app:packageDebug", ":app:packageDebugAndroidTest"]
ARTIFACTS = [
    {"kind": "main", "apk": "app/build/outputs/apk/debug/app-debug.apk",
     "metadata": "app/build/outputs/apk/debug/output-metadata.json",
     "package": "com.blackandblue.justshare", "variant": "debug", "packet_name": "main-debug.apk"},
    {"kind": "test", "apk": "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk",
     "metadata": "app/build/outputs/apk/androidTest/debug/output-metadata.json",
     "package": "com.blackandblue.justshare.test", "variant": "debugAndroidTest", "packet_name": "test-debug.apk"},
]
MAX_APK_BYTES = 200 * 1024**2
REVIEWED_PACKAGING_INPUTS = {'/Users/speketi/.android/debug.keystore': {'bytes': 2618,
                                            'mode': 420,
                                            'sha256': '332e7cd157b9ef54bf92ae09513a236e8756e0b9e3da86c9aebe1d6597e2c23b'},
 '/Users/speketi/Library/Android/sdk/build-tools/36.0.0/aapt': {'bytes': 2995488,
                                                                'mode': 493,
                                                                'sha256': '170717682f714712c5b6854af73cfe37aeda342ff422384e98d67fc1b490f49b'},
 '/Users/speketi/Library/Android/sdk/build-tools/36.0.0/apksigner': {'bytes': 2959,
                                                                     'mode': 493,
                                                                     'sha256': 'b47549e373b895ce6ca620d0c7887e674d9615ffa837a86ac601dcfd04adb0f0'},
 '/Users/speketi/Library/Android/sdk/build-tools/36.0.0/lib/apksigner.jar': {'bytes': 1100545,
                                                                             'mode': 420,
                                                                             'sha256': '3716d9311e55d2b0918a2fd9d54ba9e406c5f6abeea700b287f11259bc163dec'},
 '/Users/speketi/Library/Android/sdk/build-tools/36.0.0/source.properties': {'bytes': 62,
                                                                             'mode': 420,
                                                                             'sha256': '7dee6632e9ad6cb111da2bb99d747211e27927061b1276d040bb1d71fded5ebb'},
 '/Users/speketi/Projects/Just-Share/app/build/intermediates/packaged_manifests/debugAndroidTest/processDebugAndroidTestManifest/AndroidManifest.xml': {'bytes': 2204,
                                                                                                                                                        'mode': 420,
                                                                                                                                                        'sha256': 'd8ba5bb2383ad15825be04f0b9beab971aa7322b74866f35d45d8d187743f123'},
 '/Users/speketi/Projects/Just-Share/docs/permission-recovery-host-validation-20261006-one/result.json': {'bytes': 1042884,
                                                                                                          'mode': 384,
                                                                                                          'sha256': '7a4d7499a1c33f38d516cb86fc3ec294b9cde978fe7f097b1b972776faa88b79'},
 '/Users/speketi/Projects/Just-Share/docs/run-permission-recovery-host-validation-20261006.py': {'bytes': 57821,
                                                                                                 'mode': 420,
                                                                                                 'sha256': 'fb362f7af2bae50cd21852f2aec1f5b5c09e7c775b28974543e905fe0c798525'}}


def packaging_inputs():
    paths = [SDK_TOOLS / name for name in ("aapt", "apksigner", "lib/apksigner.jar", "source.properties")]
    paths += [DEBUG_KEYSTORE, TEST_MANIFEST, HOST_CALLER, HOST_RESULT]
    result = {}
    for path in paths:
        before = path.lstat()
        if not stat.S_ISREG(before.st_mode):
            raise ValueError("Packaging inputs must be existing regular files")
        result[str(path)] = {"sha256": sha(path), "bytes": before.st_size,
                             "mode": stat.S_IMODE(before.st_mode)}
    return result


def validate_host_proof():
    if sha(HOST_CALLER) != HOST_CALLER_SHA or sha(HOST_RESULT) != HOST_RESULT_SHA:
        raise ValueError("Accepted host caller/result changed")
    proof = json.loads(HOST_RESULT.read_text())
    host_sources = proof.get("source_pins_before", {}).get("source_files", {})
    expected = {"com.blackandblue.justshare.LocalTransferPermissionsTest",
                "com.blackandblue.justshare.domain.transfer.TransferProgressSessionTest"}
    if (proof.get("source") != HOST_VALIDATION_HEAD or not proof.get("validation_passed")
            or not proof.get("inputs_unchanged") or not proof.get("owned_cleanup")
            or proof.get("source_pins_before", {}).get("source_trees") != HOST_SOURCE_TREES
            or set(host_sources) != set(REVIEWED_SOURCE_SHA256)
            or any(host_sources[name] != digest for name, digest in REVIEWED_SOURCE_SHA256.items()
                   if name != HOME_SUCCESSOR_SOURCE_DELTA)
            or set(proof.get("tests", {})) != expected
            or any(value.get("counts") != {"tests": 4, "failures": 0, "errors": 0, "skipped": 0}
                   for value in proof["tests"].values())
            or proof.get("compile_task_observations") != {
                ":app:compileDebugKotlin": "EXECUTED", ":app:compileDebugAndroidTestKotlin": "EXECUTED"}):
        raise ValueError("Historical eight-case host/compile proof must match every current source input except the reviewed Home-only successor")


def package_task_observations(raw):
    observations = {}
    for task in PACKAGE_TASKS:
        states = re.findall(r"(?m)^> Task " + re.escape(task) + r"(?: ([^\n]*))?$", raw)
        if states != [""]:
            raise ValueError("Fresh package-task execution not observed: " + task)
        observations[task] = "EXECUTED"
    return observations


def check_work_deadline():
    if WATCH is None or time.monotonic() >= WATCH.work_deadline:
        raise TimeoutError("The unchanged packaging work deadline has expired")


def fresh_artifact(path, started_ns, limit):
    check_work_deadline()
    before = path.lstat()
    if not stat.S_ISREG(before.st_mode) or before.st_size > limit or before.st_mtime_ns < started_ns:
        raise ValueError("Missing fresh bounded regular packaging artifact")
    descriptor = os.open(path, os.O_RDONLY | os.O_NOFOLLOW)
    chunks, size = [], 0
    with os.fdopen(descriptor, "rb") as stream:
        opened = os.fstat(stream.fileno())
        while True:
            check_work_deadline()
            chunk = stream.read(1024**2)
            if not chunk:
                break
            size += len(chunk)
            if size > limit:
                raise ValueError("Packaging artifact exceeds its byte bound")
            chunks.append(chunk)
        after = os.fstat(stream.fileno())
    last = path.lstat()
    identity = lambda info: (info.st_dev, info.st_ino, info.st_size, info.st_mtime_ns, info.st_ctime_ns)
    if identity(before) != identity(opened) or identity(opened) != identity(after) or identity(after) != identity(last):
        raise ValueError("Packaging artifact changed during capture")
    return b"".join(chunks)


def inspect_zip(raw):
    check_work_deadline()
    with zipfile.ZipFile(io.BytesIO(raw)) as archive:
        members = archive.infolist()
        names = [member.filename for member in members]
        if (len(names) != len(set(names)) or not {"AndroidManifest.xml", "classes.dex"}.issubset(names)
                or sum(member.file_size for member in members) > 512 * 1024**2
                or any(member.file_size > 128 * 1024**2 or member.flag_bits & 1
                       or member.filename.startswith("/") or "\\" in member.filename
                       or ".." in Path(member.filename).parts for member in members)):
            raise ValueError("APK ZIP structure/entry bounds failed")
        for member in members:
            with archive.open(member) as stream:
                while True:
                    check_work_deadline()
                    if not stream.read(64 * 1024):
                        break  # Reading every member verifies its CRC.
        return {"entries": len(names), "manifest_present": True, "dex_present": True,
                "resources_present": "resources.arsc" in names, "all_entry_crc_verified": True}


def output_metadata(raw, entry):
    value = json.loads(raw)
    elements = value.get("elements", [])
    if (value.get("artifactType", {}).get("type") != "APK"
            or value.get("applicationId") != entry["package"] or value.get("variantName") != entry["variant"]
            or len(elements) != 1 or elements[0].get("filters") != []
            or elements[0].get("outputFile") != Path(entry["apk"]).name):
        raise ValueError("Fresh single-APK Gradle output metadata does not match the selected variant")
    element = elements[0]
    code, name = element.get("versionCode"), element.get("versionName")
    if entry["kind"] == "main":
        if not isinstance(code, int) or isinstance(code, bool) or (code, name) != (9, "1.0.5"):
            raise ValueError("Accepted production version changed")
    elif (code is not None and (not isinstance(code, int) or isinstance(code, bool) or code < 0)) or (name is not None and not isinstance(name, str)):
        raise ValueError("Fresh Android-test Gradle version metadata is invalid")
    return {"package": entry["package"], "version_code": code, "version_name": name}


def public_badging(raw, entry, metadata):
    packages = re.findall(r"(?m)^package: ([^\n]+)$", raw)
    if len(packages) != 1:
        raise ValueError("SDK aapt did not identify exactly one APK package")
    values = dict(re.findall(r"([A-Za-z][A-Za-z0-9]*)='([^']*)'", packages[0]))
    expected_version = ({"versionCode": str(metadata["version_code"]), "versionName": metadata["version_name"]}
                        if entry["kind"] == "main" else TEST_MANIFEST_VERSION)
    if values.get("name") != metadata["package"] or any(values.get(key) != value for key, value in expected_version.items()):
        raise ValueError("SDK package/version metadata differs from the selected fresh output/compiled manifest")
    result = {"package": metadata["package"], "version_code": int(values["versionCode"]) if values["versionCode"] else None,
              "version_name": values["versionName"] or None}
    return result


def public_instrumentation(raw, entry):
    stack, instruments, roots = [], [], 0
    for line in raw.splitlines():
        if "\t" in line:
            raise ValueError("SDK manifest tree indentation must use spaces")
        element = re.fullmatch(r"( *)E: ([A-Za-z_][A-Za-z0-9_.:-]*) \(line=\d+\)", line)
        if element:
            indent, name = len(element.group(1)), element.group(2)
            while stack and stack[-1]["indent"] >= indent:
                stack.pop()
            if not stack:
                roots += 1
                if roots != 1 or name != "manifest":
                    raise ValueError("SDK manifest tree must have exactly one manifest root")
            elif indent != stack[-1]["indent"] + 2:
                raise ValueError("SDK manifest element indentation is not an immediate child")
            current = {"indent": indent, "name": name, "attributes": {}}
            if name == "instrumentation":
                if not stack or stack[-1]["name"] != "manifest":
                    raise ValueError("Instrumentation must be a direct manifest child")
                instruments.append(current)
            stack.append(current)
            continue
        attribute = re.fullmatch(r"( *)A: ([^=]+)=(.*)", line)
        if attribute and stack:
            if len(attribute.group(1)) != stack[-1]["indent"] + 2:
                raise ValueError("SDK manifest attribute is not an immediate element child")
            if stack[-1]["name"] == "instrumentation":
                key = re.fullmatch(r"android:(name|targetPackage)(?:\(0x[0-9a-fA-F]{8}\))?", attribute.group(2))
                if key:
                    value = re.fullmatch(r'"([^"\\]*)"(?: \(Raw: "[^"\\]*"\))?', attribute.group(3))
                    if not value or key.group(1) in stack[-1]["attributes"]:
                        raise ValueError("Instrumentation attributes must be unique direct literal strings")
                    stack[-1]["attributes"][key.group(1)] = value.group(1)
            continue
        if line.lstrip().startswith(("E:", "A:")):
            raise ValueError("Malformed SDK manifest tree element/attribute")
    if roots != 1:
        raise ValueError("SDK manifest tree root is missing")
    if entry["kind"] == "main":
        if instruments:
            raise ValueError("Production debug APK unexpectedly declares instrumentation")
        return {"instrumentation_count": 0}
    if len(instruments) != 1 or instruments[0]["attributes"] != {
            "name": "androidx.test.runner.AndroidJUnitRunner", "targetPackage": "com.blackandblue.justshare"}:
        raise ValueError("Android-test APK must declare exactly one direct expected runner/target")
    return {"instrumentation_count": 1, "test_runner": instruments[0]["attributes"]["name"],
            "test_target": instruments[0]["attributes"]["targetPackage"]}


def public_certificate(raw):
    counts = re.findall(r"(?m)^Number of signers: (\d+)$", raw)
    digests = re.findall(r"(?m)^Signer #1 certificate SHA-256 digest: ([0-9a-fA-F]{64})$", raw)
    if len(re.findall(r"(?m)^Verifies$", raw)) != 1 or counts != ["1"] or len(digests) != 1:
        raise ValueError("Installed apksigner did not verify exactly one signing certificate")
    schemes = dict(re.findall(r"(?m)^Verified using (v\d[^:]*): (true|false)$", raw))
    if not schemes or not any(value == "true" for value in schemes.values()):
        raise ValueError("Installed apksigner reported no verified APK signature scheme")
    return {"signer_count": 1, "certificate_sha256": digests[0].lower(), "verified_schemes": schemes}


def main():
    global WATCH
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, help="Exact committed caller HEAD; preparation is not a runtime/coordinator grant")
    args = parser.parse_args()
    if (not re.fullmatch(r"[0-9a-f]{40}", args.source) or not SOURCE_ACCEPTED
            or not REVIEWED_PACKAGING_INPUTS or not PACKAGING_ADMISSION_READY):
        raise ValueError("A full committed source and source-accepted packaging preparation are required before admission")
    pins = fingerprints()
    if (pins["source"] != args.source or pins["source_trees"] != REVIEWED_SOURCE_TREES
            or pins["source_files"] != REVIEWED_SOURCE_SHA256 or pins["build_blobs"] != REVIEWED_BUILD_BLOBS
            or {name: pins["files"][name] for name in REVIEWED_BUILD_SHA256} != REVIEWED_BUILD_SHA256
            or pins["caller_working_blob"] != pins["caller_blob"]
            or {key: pins[key] for key in REVIEWED_CONFIGURED_PINS} != REVIEWED_CONFIGURED_PINS
            or pins["packaging_inputs"] != REVIEWED_PACKAGING_INPUTS):
        raise ValueError("Exact committed source/build/caller/configured/JDK/helper/signing/SDK inputs changed")
    command("git", "merge-base", "--is-ancestor", APP_SOURCE, args.source)
    command("git", "merge-base", "--is-ancestor", PREPARATION_BASE_HEAD, args.source)
    check_layout()
    android = declared_android_methods()
    if android != REVIEWED_ANDROID_DECLARATIONS:
        raise ValueError("Accepted 21 Android declarations changed")
    validate_host_proof()
    if any(os.environ.get(key) for key in ("ANDROID_USER_HOME", "ANDROID_SDK_HOME", "ANDROID_PREFS_ROOT")):
        raise ValueError("The reviewed default debug signing location must remain configured")
    if PACKET.exists() or PACKET.is_symlink() or CLAIM.exists() or CLAIM.is_symlink():
        raise ValueError("This one-use packaging packet/claim path has already been used; no automatic retry")
    sys.dont_write_bytecode = True
    sys.path.insert(0, str(HELPER.parent))
    spec = importlib.util.spec_from_file_location("unchanged_justshare_debug_packaging_watchdog", HELPER)
    if spec is None or spec.loader is None:
        raise ValueError("Maintained helper cannot be loaded")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    pressure = int(command("sysctl", "-n", "kern.memorystatus_vm_pressure_level"))
    free = shutil.disk_usage(REPO).free
    if pressure > 2 or free < 12 * 1024**3:
        raise ValueError("Pressure above 2 or disk below 12 GiB; admission refused")
    if heavy(module.processes(timeout=1), executables=executable_inventory(timeout=1)):
        raise ValueError("Foreign Java/Gradle/emulator/engine work is live; admission refused")
    started_ns = time.time_ns()
    claim = {"actor": "Just-Share one-use configured debug APK-pair packaging caller", "source": args.source,
             "accepted_app_source": APP_SOURCE, "source_pins_before": pins, "pressure": pressure,
             "free_bytes": free, "started_after_epoch_ns": started_ns, "coordinator_grant": False,
             "assemble_tasks": ASSEMBLE_TASKS, "jvm_tests_requested": False, "android_device_cases_executed": 0,
             "provider_actions": False, "declared_android_methods_unrun": android}
    write_private(CLAIM, json_bytes(claim))
    PACKET.mkdir(mode=0o700)
    write_private(PACKET / "actor-claim.json", json_bytes(claim))
    argv = ["rtk", "proxy", str(REPO / "gradlew"), *ASSEMBLE_TASKS, "--offline", "--no-daemon", "--max-workers=1",
            "-Dorg.gradle.jvmargs=-Xmx1536m", "-Pkotlin.compiler.execution.strategy=in-process", "--console=plain"]
    env = dict(os.environ, JAVA_HOME=str(JAVA_HOME), PATH=str(JAVA_HOME / "bin") + os.pathsep + os.environ.get("PATH", ""))
    result = {"source": args.source, "accepted_app_source": APP_SOURCE, "source_pins_before": pins,
              "source_pins_after": None, "argv": argv, "coordinator_grant": False, "jvm_tests_requested": False,
              "android_device_cases_executed": 0, "provider_actions": False, "declared_android_methods_unrun": android,
              "foreign_work_observations": [], "package_task_observations": {}, "artifacts": {}, "failures": [],
              "inputs_unchanged": False, "packaging_passed": False, "certificate_pair_matches": False}
    last_pressure = 0.0

    def shared_boundary():
        nonlocal last_pressure
        snapshot = executable_inventory(timeout=min(1, WATCH.work_deadline - time.monotonic()))
        WATCH.sample()
        # Use the full process table: the helper intentionally filters its preexisting processes.
        remaining = WATCH.work_deadline - time.monotonic()
        if remaining <= 0:
            raise TimeoutError("The batch work deadline expired before metadata inventory")
        rows = module.processes(timeout=min(1, remaining))
        foreign = heavy(rows, WATCH.owned, executables=snapshot)
        if foreign:
            result["foreign_work_observations"].append(foreign)
            raise RuntimeError("Foreign heavy work appeared; stop only this caller's owned batch")
        if time.monotonic() - last_pressure >= 1:
            last_pressure = time.monotonic()
            if int(command("sysctl", "-n", "kern.memorystatus_vm_pressure_level",
                           timeout=min(1, WATCH.work_deadline - time.monotonic()))) > 2:
                raise RuntimeError("Pressure above 2; stop only this caller's owned batch")

    try:
        WATCH = module.Watchdog(REPO, PACKET, "JUSTSHARE-DEBUG-PACKAGING-20261006-ONE", seconds=300)
        shared_boundary()
        WATCH.run(argv, env, "gradle.log", callback=shared_boundary)
        result["package_task_observations"] = package_task_observations((PACKET / "gradle.log").read_text())
        for entry in ARTIFACTS:
            shared_boundary()
            raw = fresh_artifact(REPO / entry["apk"], started_ns, MAX_APK_BYTES)
            metadata_raw = fresh_artifact(REPO / entry["metadata"], started_ns, 256 * 1024)
            metadata = output_metadata(metadata_raw, entry)
            structure = inspect_zip(raw)
            captured = PACKET / entry["packet_name"]
            write_private(captured, raw)
            write_private(PACKET / (entry["kind"] + "-output-metadata.json"), metadata_raw)
            digest = hashlib.sha256(raw).hexdigest()
            badging_log = entry["kind"] + "-badging.log"
            xmltree_log = entry["kind"] + "-manifest-xmltree.log"
            cert_log = entry["kind"] + "-certificate.log"
            shared_boundary()
            WATCH.run(["rtk", "proxy", str(SDK_TOOLS / "aapt"), "dump", "badging", str(captured)],
                      env, badging_log, callback=shared_boundary)
            WATCH.run(["rtk", "proxy", str(SDK_TOOLS / "aapt"), "dump", "xmltree", str(captured), "AndroidManifest.xml"],
                      env, xmltree_log, callback=shared_boundary)
            # The pinned installed shell script converts -JXmx256m into Java -Xmx256m.
            WATCH.run(["rtk", "proxy", str(SDK_TOOLS / "apksigner"), "-JXmx256m", "verify", "--verbose",
                       "--print-certs", str(captured)], env, cert_log, callback=shared_boundary)
            public = public_badging((PACKET / badging_log).read_text(), entry, metadata)
            public.update(public_instrumentation((PACKET / xmltree_log).read_text(), entry))
            cert = public_certificate((PACKET / cert_log).read_text())
            check_work_deadline()
            if sha(captured) != digest or sha(REPO / entry["apk"]) != digest:
                raise ValueError("Packaged APK changed during installed SDK verification")
            result["artifacts"][entry["kind"]] = {"apk_sha256": digest, "bytes": len(raw),
                "metadata_sha256": hashlib.sha256(metadata_raw).hexdigest(), "zip_structure": structure,
                "public_metadata": public, "certificate": cert,
                "aapt_output_sha256": sha(PACKET / badging_log), "manifest_xmltree_output_sha256": sha(PACKET / xmltree_log),
                "apksigner_output_sha256": sha(PACKET / cert_log)}
        result["certificate_pair_matches"] = (result["artifacts"]["main"]["certificate"]["certificate_sha256"]
                                              == result["artifacts"]["test"]["certificate"]["certificate_sha256"])
        if not result["certificate_pair_matches"]:
            raise ValueError("Debug main/test APK signing certificates differ")
    except BaseException:
        result["failures"].append(traceback.format_exc())
    finally:
        try:
            result["owned_cleanup"] = WATCH.cleanup() if WATCH is not None else True
        except BaseException:
            result["owned_cleanup"] = False
            result["failures"].append(traceback.format_exc())
        try:
            result["source_pins_after"] = fingerprints(require_clean=False)
            result["inputs_unchanged"] = result["source_pins_after"] == pins
            if not result["inputs_unchanged"]:
                raise ValueError("Source/build/caller/configured/JDK/helper/signing/SDK inputs changed during packaging")
        except BaseException:
            result["failures"].append(traceback.format_exc())
        result.update(owned_identities=WATCH.owned if WATCH is not None else {}, samples=WATCH.samples if WATCH is not None else [],
                      seconds=round(time.monotonic() - WATCH.start, 3) if WATCH is not None else 0)
        log = PACKET / "gradle.log"
        result["raw_log_sha256"] = sha(log) if log.is_file() else None
        if WATCH is not None and time.monotonic() >= WATCH.deadline:
            result["failures"].append("Unchanged 300-second total deadline expired")
        result["packaging_passed"] = (not result["failures"] and result["owned_cleanup"] and result["inputs_unchanged"]
                                      and result["certificate_pair_matches"] and len(result["artifacts"]) == 2)
        write_private(PACKET / "result.json", json_bytes(result))
        print(json.dumps({key: value for key, value in result.items()
                          if key not in ("source_pins_before", "source_pins_after", "samples", "owned_identities")}))
    raise SystemExit(0 if result["packaging_passed"] else 1)


if __name__ == "__main__":
    main()
