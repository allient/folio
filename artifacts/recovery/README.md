# Notebook recovery snapshot

`Older-emulator-notebooks.knotes` is a portable app backup created from the notebook database found on the already configured Android emulator before reader QA. It contains six notebooks and no media assets. Its archive CRC and each reconstructed notebook payload were verified.

This is a separate emulator snapshot. It was not merged into the physical tablet's fresh installation, because there was no evidence that the two app databases represented the same set of notes. Import this `.knotes` file from the app's notebook backup/restore flow only if these are the notebooks you want to restore.
